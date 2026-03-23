# Flutter OCR PoC

On-device Arabic ID card OCR using PaddleOCR (PP-OCRv5) with Paddle Lite and ONNX Runtime. Runs entirely on-device with no cloud dependencies.

## How to Use

1. Open the app and pick an image — camera, gallery, or file picker
2. Tap **Recognize Text**
3. View the recognized text blocks with bounding boxes and confidence scores

## Pipeline

Each image goes through a multi-stage pipeline. Per-region stages run independently for every detected text box.

```
Input Image
    |
    v
[Detection] ─── PP-OCRv5 mobile det + DB algorithm → quadrilateral boxes
    |
    v
For each detected region:
    |
    +── [Crop]         ─── Axis-aligned bounding rect from quad corners
    |
    +── [Trim]         ─── Otsu threshold + projection → tight content bounds
    |
    +── [Preprocess]   ─── Bilateral filter → sharpen → normalize → super-res
    |
    +── [CLAHE]        ─── Adaptive local contrast enhancement
    |
    +── [Recognize]    ─── ONNX Runtime inference → CTC greedy decode
    |
    +── [Arabic RTL]   ─── Smart reversal for Arabic letter runs
```

### Detection

- **Model**: `PP-OCRv5_mobile_det.nb` (Paddle Lite)
- **Algorithm**: DB (Differentiable Binarization) — learns a threshold map that makes binarization differentiable, enabling end-to-end training
- **Post-processing**: Two-pass connected component labeling on the binary map, then axis-aligned bounding rects with unclip expansion
- **Thresholds**: `DB_THRESH=0.2` (pixel probability), `BOX_THRESH=0.3` (average score per component), `UNCLIP_RATIO=1.8` (expansion factor)
- **Normalization**: ImageNet mean/std (`[0.485, 0.456, 0.406]` / `[0.229, 0.224, 0.225]`), BGR channel order
- **Input**: Max side 1280px, resized to multiples of 32

### Crop

Extracts an axis-aligned bounding rectangle from the quadrilateral corners returned by detection. Simply computes `min/max` of x and y coordinates and clips to source image bounds.

### Trim

Removes excess background added by the unclip expansion:

1. Convert to grayscale luminance
2. **Otsu's method** — automatic thresholding by maximizing inter-class variance
3. **Row/column projection** — scan binary rows and columns to find the first/last foreground pixel in each direction
4. Add 3px padding around the tight bounding box

### Preprocess

Four-stage pipeline applied to each cropped text region before recognition:

1. **Bilateral filter** (radius=2, sigma_color=75, sigma_space=75) — edge-preserving denoise that smooths flat areas while keeping text edges sharp
2. **Unsharp mask** (3x3 Gaussian, amount=1.5) — sharpens blurred text by subtracting a Gaussian-blurred copy
3. **Brightness normalization** (target mean=127) — multiplies pixel values to center the mean, evening out lighting differences across the card
4. **ESPCN 2x super-resolution** (only when crop height < 40px) — efficient sub-pixel convolutional network upscales small text. Operates on the Y channel in YCbCr color space, then recombines with upscaled Cb/Cr via bilinear interpolation

### CLAHE

Contrast Limited Adaptive Histogram Equalization, applied after preprocessing:

- **Tile grid**: 8x8 — divides the image into 64 tiles, each with its own histogram
- **Clip limit**: 2.0 — caps histogram bins to prevent noise amplification
- **Interpolation**: Bilinear between adjacent tile CDFs for seamless transitions
- **Color preservation**: Applied to luminance only, then original color is restored via ratio scaling (output_lum / input_lum)

### Recognition

- **Model**: `arabic_PP-OCRv5_mobile_rec.onnx` (ONNX Runtime)
- **Input**: Height fixed at 48px, width proportional to aspect ratio, normalized with mean=0.5 / std=0.5
- **Decoder**: CTC greedy decode — argmax along the time axis, collapse consecutive duplicates, remove blank tokens (index 0)
- **Dictionary**: `ppocr_keys_arabic_v5.txt` — loaded with blank token at index 0 and space token appended

### Arabic RTL

CTC decodes left-to-right, but Arabic reads right-to-left. The smart reversal algorithm:

1. Splits text into runs — each run is either all Arabic letters or all non-Arabic characters
2. **Arabic letter detection**: Unicode ranges U+0600–U+06FF, U+0750–U+077F, U+FB50–U+FDFF, U+FE70–U+FEFF, excluding Arabic-Indic digits
3. **Arabic-Indic digits** (U+0660–U+0669, U+06F0–U+06F9) are treated as LTR — they stay in their original order
4. Reverses characters within each Arabic letter run
5. Reverses the overall run order (RTL reordering)

## Models

| File | Purpose | Format | Notes |
|------|---------|--------|-------|
| `PP-OCRv5_mobile_det.nb` | Text detection | Paddle Lite | DB algorithm, finds text region quadrilaterals |
| `arabic_PP-OCRv5_mobile_rec.onnx` | Arabic text recognition | ONNX | PP-OCRv5 Arabic rec, CTC output |
| `PP-OCRv5_mobile_rec.nb` | Multilingual recognition | Paddle Lite | Legacy fallback when ONNX is not configured |
| `espcn_x2.onnx` | 2x super-resolution | ONNX | ESPCN upscaler for small text crops (height < 40px) |
| `ppocr_keys_arabic_v5.txt` | Character dictionary | Text | One character per line, used for CTC index→character mapping |

## Debug Images

When `AppConstants.saveDebugImages` is `true` (default), the engine saves every intermediate bitmap to the device cache:

```
<cacheDir>/ocr_debug/<timestamp>/
  detection_overlay.png           # Full image with red bounding boxes
  region_000/
    01_cropped.png
    02_trimmed.png
    03_preprocessed.png
    04_clahe.png
  region_001/
    ...
```

Pull them from the device:

```bash
adb shell ls /data/data/com.example.flutter_ocr_poc/cache/ocr_debug/
adb pull /data/data/com.example.flutter_ocr_poc/cache/ocr_debug/ ./debug_images/
```

Only the 5 most recent sessions are kept to prevent cache bloat.

## Architecture

Layered DDD with Riverpod state management:

| Layer | Purpose |
|-------|---------|
| `presentation/` | UI widgets, screens, Riverpod providers |
| `application/` | Use cases / interactors |
| `domain/` | Entities (`OcrResult`, `TextBlock`, `BoundingBox`) and repository interfaces |
| `data/` | Repository implementations, models (DTOs), mappers, native data source |
| `core/` | Constants, error types, shared utilities |

Native OCR lives in `android/.../ocr/`:

| File | Role |
|------|------|
| `PaddleOcrEngine.kt` | Full pipeline: detection, crop, trim, preprocess, CLAHE, recognize |
| `OcrMethodHandler.kt` | MethodChannel bridge between Flutter and the engine |
| `RecOnnxRunner.kt` | ONNX Runtime wrapper for Arabic recognition model |
| `ClaheProcessor.kt` | Contrast Limited Adaptive Histogram Equalization |
| `DebugImageSaver.kt` | Saves intermediate pipeline bitmaps per region |
| `preprocessing/` | Bilateral filter, sharpen, brightness normalization, super-resolution |

## Project Structure

```
lib/
  main.dart
  core/
    constants/         # AppConstants (model filenames, flags, thresholds)
    error/             # Exception types
  domain/
    entities/          # OcrResult, TextBlock, BoundingBox
    repositories/      # Repository interfaces
  data/
    datasources/native/  # MethodChannel communication
    mappers/             # Model <-> Entity conversion
    models/              # DTOs matching native platform maps
    repositories/        # Repository implementations
  application/         # Use cases
  presentation/        # Screens, widgets, providers
android/app/src/main/kotlin/.../ocr/
  PaddleOcrEngine.kt
  OcrMethodHandler.kt
  RecOnnxRunner.kt
  ClaheProcessor.kt
  DebugImageSaver.kt
  preprocessing/
```

## Setup

See [SETUP.md](SETUP.md) for build instructions and model preparation.
