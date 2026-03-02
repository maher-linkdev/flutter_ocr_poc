# Flutter OCR POC — Setup Guide

This project supports **two OCR engines**:

1. **PaddleOCR** (Paddle Lite) — best for **Arabic** and multilingual; requires .nb models.
2. **ML Kit Text Recognition v2** — on-device, offline; supports Latin, Chinese, Devanagari, Japanese, Korean. **Does NOT support Arabic.**

The Dart side uses **DDD architecture** with **Riverpod** state management. You can switch engines from the app UI.

## Architecture Overview

```
┌─────────────────────────────────────────────┐
│              PRESENTATION                    │
│  OcrPage, BoundingBoxOverlay, ResultView    │
├─────────────────────────────────────────────┤
│              APPLICATION                     │
│  OcrNotifier, OcrState, Providers           │
├─────────────────────────────────────────────┤
│                DOMAIN                        │
│  OcrResult, TextBlock, BoundingBox          │
│  OcrRepository (interface), UseCases        │
├─────────────────────────────────────────────┤
│                  DATA                        │
│  Models (DTOs), Mappers, NativeDataSource   │
│  OcrRepositoryImpl                          │
├─────────────────────────────────────────────┤
│          NATIVE (MethodChannel)              │
│  Android: Kotlin + Paddle Lite Java API     │
│  iOS: Swift + Paddle Lite C API             │
└─────────────────────────────────────────────┘
```

## Prerequisites

- Flutter SDK 3.9.2+
- Android Studio / Xcode
- For Android: NDK r20b+ (for Paddle Lite native libs)
- For iOS: Xcode 14+ with arm64 support

## Step 1: Download Paddle Lite Libraries

### Android

1. Download Paddle Lite Android inference library (v2.13+):
   - Visit: https://github.com/PaddlePaddle/Paddle-Lite/releases
   - Download: `inference_lite_lib.android.armv8.gcc.c++_shared.with_extra.with_cv.tar.gz`

2. Extract and copy:
   ```bash
   # Copy the JNI shared library
   cp cxx/lib/libpaddle_light_api_shared.so \
      android/app/src/main/jniLibs/arm64-v8a/libpaddle_lite_jni.so

   # Copy the Java predictor JAR
   mkdir -p android/app/libs
   cp java/jar/PaddlePredictor.jar android/app/libs/
   ```

3. For armeabi-v7a support, also download the armv7 version and copy similarly.

### iOS

1. Download Paddle Lite iOS framework (v2.13+):
   - Visit: https://github.com/PaddlePaddle/Paddle-Lite/releases
   - Download: `inference_lite_lib.ios.armv8.with_cv.with_extra.with_log.tiny_publish.tar.gz`

2. Extract and add to Xcode project:
   - Drag `PaddleLite.framework` into `ios/Runner/` in Xcode
   - In target settings → "Frameworks, Libraries, and Embedded Content"
   - Set to "Embed & Sign"

3. Create a bridging header (`ios/Runner/Runner-Bridging-Header.h`):
   ```objc
   #import "paddle_api.h"
   ```

## Step 2: Download OCR Models

This app is configured for **PP-OCRv5 detection** + **Arabic PP-OCRv5 recognition** (best for Arabic ID cards). You need:

- **Detection:** `PP-OCRv5_mobile_det.nb`
- **Recognition:** `arabic_PP-OCRv5_mobile_rec.nb` (Arabic-specific)
- **Classification:** `ch_ppocr_mobile_v2.0_cls_slim_opt.nb`
- **Labels:** `assets/labels/ppocr_keys_arabic.txt` (included)

**There is no direct download for `arabic_PP-OCRv5_mobile_rec.nb`.** Paddle and Paddle-Lite-Demo only ship the default **PP-OCRv5_mobile** (Chinese/multilingual) in `.nb` form. The Arabic v5 model is only published in Paddle inference format (Hugging Face: [PaddlePaddle/arabic_PP-OCRv5_mobile_rec](https://huggingface.co/PaddlePaddle/arabic_PP-OCRv5_mobile_rec)), so you must **convert it to `.nb`** yourself (see below), or **use the multilingual rec for now** (you already have `PP-OCRv5_mobile_rec.nb` — switch constants to that and `ppocr_keys_ocrv5.txt` to run the app; Arabic text will be less accurate).

### Option A: PP-OCRv5 + Arabic recognition (recommended for Arabic IDs)

1. **Detection and classification**  
   Get or convert to `.nb` and place in `assets/models/`:
   - `PP-OCRv5_mobile_det.nb`
   - `ch_ppocr_mobile_v2.0_cls_slim_opt.nb`

2. **Arabic recognition model**  
   - **Source:** [Hugging Face – PaddlePaddle/arabic_PP-OCRv5_mobile_rec](https://huggingface.co/PaddlePaddle/arabic_PP-OCRv5_mobile_rec) (inference weights + config).  
   - The repo provides Paddle inference format (e.g. `inference.pdiparams` + structure). To use on device you must **convert to Paddle Lite `.nb`** with the same Paddle Lite / `opt` version as your app (to avoid crashes):
     ```bash
     # Install Paddle Lite opt (match your app’s Paddle Lite version)
     pip install paddlelite

     # If you have inference.pdmodel + inference.pdiparams (e.g. exported from PaddleOCR):
     paddle_lite_opt \
       --model_file=inference.pdmodel \
       --param_file=inference.pdiparams \
       --optimize_out=arabic_PP-OCRv5_mobile_rec \
       --valid_targets=arm \
       --optimize_out_type=naive_buffer
     ```
   - If the Hugging Face model is in PIR format (e.g. `inference.json` + `inference.pdiparams` only), export to legacy `pdmodel`/`pdiparams` first (e.g. via PaddleOCR export script or Paddle API), then run `paddle_lite_opt` as above.
   - Copy the output:  
     `cp arabic_PP-OCRv5_mobile_rec.nb assets/models/`

3. **Label file**  
   `assets/labels/ppocr_keys_arabic.txt` is already in the project (PaddleOCR `arabic_dict.txt` order). No extra download needed.

**Use multilingual rec until you have the Arabic .nb:** In `lib/core/constants/app_constants.dart` set `recModelFileName = 'PP-OCRv5_mobile_rec.nb'` and `labelFileName = 'ppocr_keys_ocrv5.txt'`. Ensure `assets/models/PP-OCRv5_mobile_rec.nb` and `assets/labels/ppocr_keys_ocrv5.txt` exist. The app will run; Arabic ID text may be less accurate. Switch back to `arabic_PP-OCRv5_mobile_rec.nb` and `ppocr_keys_arabic.txt` after you generate the Arabic `.nb`.

### Option B: PP-OCRv3 Slim (alternative, ~5.9MB total)

Download pre-converted `.nb` models from the Paddle-Lite-Demo repository:

```bash
# Clone the demo repo
git clone -b feature/paddle-x https://github.com/PaddlePaddle/Paddle-Lite-Demo.git

# Models are in: Paddle-Lite-Demo/ocr/assets/models/
```

Or convert models yourself using `paddle_lite_opt`:

```bash
pip install paddlelite

# Detection model
paddle_lite_opt \
  --model_file=ch_PP-OCRv3_det_slim/inference.pdmodel \
  --param_file=ch_PP-OCRv3_det_slim/inference.pdiparams \
  --optimize_out=ch_PP-OCRv3_det_slim_infer \
  --valid_targets=arm \
  --optimize_out_type=naive_buffer

# Recognition model
paddle_lite_opt \
  --model_file=ch_PP-OCRv3_rec_slim/inference.pdmodel \
  --param_file=ch_PP-OCRv3_rec_slim/inference.pdiparams \
  --optimize_out=ch_PP-OCRv3_rec_slim_infer \
  --valid_targets=arm \
  --optimize_out_type=naive_buffer

# Classification model
paddle_lite_opt \
  --model_file=ch_ppocr_mobile_v2.0_cls_slim/inference.pdmodel \
  --param_file=ch_ppocr_mobile_v2.0_cls_slim/inference.pdiparams \
  --optimize_out=ch_ppocr_mobile_v2.0_cls_slim_opt \
  --valid_targets=arm \
  --optimize_out_type=naive_buffer
```

### Option C: PP-OCRv3 Full (~16.2MB total)

For higher accuracy, use the non-slim models. Follow the same conversion process with the full model files.

### Place Models

For **PP-OCRv5 + Arabic** (Option A), ensure:

- `assets/models/PP-OCRv5_mobile_det.nb`
- `assets/models/arabic_PP-OCRv5_mobile_rec.nb`
- `assets/models/ch_ppocr_mobile_v2.0_cls_slim_opt.nb`
- `assets/labels/ppocr_keys_arabic.txt` (already in repo)

For **PP-OCRv3** (Options B/C), copy the `.nb` files to the assets directory:

```bash
cp ch_PP-OCRv3_det_slim_infer.nb   assets/models/
cp ch_PP-OCRv3_rec_slim_infer.nb   assets/models/
cp ch_ppocr_mobile_v2.0_cls_slim_opt.nb assets/models/
```

## Step 3: Character Dictionary

For the current **Arabic** setup, `assets/labels/ppocr_keys_arabic.txt` is already included (PaddleOCR `arabic_dict.txt`). Do not change the label file unless you switch to a different recognition model.

For PP-OCRv3 or multilingual v5, use the matching dictionary (e.g. `ppocr_keys_v1.txt` or `ppocr_keys_ocrv5.txt`) and set `labelFileName` in `app_constants.dart` accordingly.

## Reinstalling .nb files (version mismatch fix)

**Yes — reinstalling the .nb files can help** if the app crashes (e.g. SIGSEGV) because the current .nb were built with a different Paddle Lite opt version than the runtime in your app.

**Option 1: Re-download pre-built .nb (easiest)**  
Use the same .nb and the same Paddle Lite libs as the official demo so versions match:

```bash
cd /path/to/flutter_ocr_poc/scripts
chmod +x reinstall_nb_models.sh
./reinstall_nb_models.sh
```

This downloads PP-OCRv5_mobile det/rec/cls .nb from Paddle-Lite-Demo’s server and copies them into `assets/models/`. Then **use the Paddle Lite Android libs from the same source**: clone [Paddle-Lite-Demo](https://github.com/PaddlePaddle/Paddle-Lite-Demo) (branch `feature/paddle-x`), run `libs/download.sh`, and copy the generated `cxx/lib/libpaddle_*` (and `libc++_shared.so` if present) into your app’s `jniLibs/` so the runtime matches the .nb.

**To try Arabic rec:** run `./reinstall_nb_models.sh arabic` — installs det/cls and tries to build `arabic_PP-OCRv5_mobile_rec.nb` (no pre-built download); see `scripts/README_arabic_rec.md` if the build fails.

**Option 2: Re-convert from source with your version**  
Install Paddle Lite opt with the **exact** version you use in the app (`pip install paddlelite==<version>`), get inference models in legacy format (`.pdmodel` + `.pdiparams`), run `paddle_lite_opt` to produce new .nb, and replace the files in `assets/models/`. Then the .nb match your app’s libs.

## Why the v5 model might not run (crashes / init failures)

Common reasons and fixes:

1. **Paddle Lite vs .nb version mismatch (SIGSEGV / null pointer)**  
   The `.nb` files must be produced by the **same** Paddle Lite **opt** version as the **runtime** library in your app (`libpaddle_lite_jni.so` / `PaddlePredictor.jar`). If the v5 `.nb` was built with e.g. opt **2.14-rc** and your app uses a different Paddle Lite build (e.g. **2.13** or a dev commit), the engine can crash (SIGSEGV) when loading or running the rec model.  
   **Fix:** Use one consistent Paddle Lite version: download the **same** release from [Paddle-Lite releases](https://github.com/PaddlePaddle/Paddle-Lite/releases) for both (a) the **opt** tool when converting models to `.nb`, and (b) the **Android libs** you put in `jniLibs/` and `libs/`. Re-convert your v5 models with that opt, or replace the app’s native libs with the same version.

2. **Missing or misnamed native library**  
   If `libpaddle_lite_jni.so` (or `libc++_shared.so` if required) is missing or wrong ABI, you get `UnsatisfiedLinkError` and the app won’t load the engine.  
   **Fix:** Ensure `android/app/src/main/jniLibs/arm64-v8a/libpaddle_lite_jni.so` (and, if needed, `libc++_shared.so`) come from the same Paddle Lite Android package. Some builds require `libc++_shared.so` next to the JNI lib.

3. **Wrong or missing model/label assets**  
   If the app is configured for `arabic_PP-OCRv5_mobile_rec.nb` but that file isn’t in `assets/models/`, init fails with “Cannot copy asset …”.  
   **Fix:** Either add the missing `.nb` or switch to the multilingual rec: in `app_constants.dart` set `recModelFileName = 'PP-OCRv5_mobile_rec.nb'` and `labelFileName = 'ppocr_keys_ocrv5.txt'`, and ensure those assets exist.

4. **Using pre-built .nb from another source**  
   If you took `.nb` files from Paddle-Lite-Demo or another repo, they were built with that project’s Paddle Lite version. If your app uses a different lib version, you can get crashes.  
   **Fix:** Align versions as in (1), or build your own `.nb` with the opt that matches your app’s Paddle Lite.

## Solutions for accurate Arabic text (recognition)

If **detection is good but recognized text is wrong** (e.g. Arabic ID text):

1. **Rec input height**  
   The app uses recognition input **height 48** (PP-OCRv5 default). No change needed unless you tune further.

2. **Use the Arabic recognition model — ONNX path (recommended)**  
   Run `python scripts/convert_arabic_to_onnx.py`, copy the output to `assets/models/`, and in `app_constants.dart` set `recOnnxFileName = 'arabic_PP-OCRv5_mobile_rec.onnx'` and `labelFileName = 'ppocr_keys_arabic.txt'`. See **scripts/ARABIC_OFFLINE_OPTIONS.md**. For the legacy .nb path (see Option A above), export from PaddleOCR and convert to `.nb`. See **scripts/README_arabic_rec.md**. (see “Option A: PP-OCRv5 + Arabic recognition” above). Legacy path: export from PaddleOCR (with `Global.export_with_pir=False`) and then convert to `.nb` with `paddle_lite_opt`. See **scripts/README_arabic_rec.md** and **scripts/export_arabic_rec_to_nb.py** for steps and an automated attempt.

3. **Label file**  
   When using the Arabic rec model, set `labelFileName = 'ppocr_keys_arabic.txt'` (already in `assets/labels/`).

## Step 4: Run the App

```bash
# Install Flutter dependencies
flutter pub get

# Run on Android
flutter run -d android

# Run on iOS
flutter run -d ios
```

## Step 5: Enable Real Inference

The native code currently returns **stub results** for build verification. Once you've completed Steps 1-3:

### Android (`PaddleOcrEngine.kt`)

1. Uncomment the Paddle Lite import and predictor initialization in `initialize()`
2. Replace `createStubResults()` call in `recognizeText()` with the actual pipeline
3. Uncomment the preprocessing and postprocessing utility methods

### iOS (`PaddleOcrEngine.swift`)

1. Add the Paddle Lite bridging header
2. Uncomment the predictor initialization in `initialize()`
3. Replace `createStubResults()` call in `recognizeText()` with the actual pipeline

## Project Structure

```
lib/
├── core/                          # App-wide utilities
│   ├── constants/app_constants.dart
│   ├── error/
│   │   ├── failures.dart          # Domain failures
│   │   └── exceptions.dart        # Data exceptions
│   └── utils/either_extensions.dart
│
├── domain/                        # Pure business logic
│   ├── entities/
│   │   ├── ocr_result.dart
│   │   ├── text_block.dart
│   │   └── bounding_box.dart
│   ├── repositories/
│   │   └── ocr_repository.dart    # Abstract interface
│   ├── usecases/
│   │   ├── base_usecase.dart
│   │   ├── initialize_ocr_engine.dart
│   │   ├── recognize_text.dart
│   │   └── dispose_ocr_engine.dart
│   └── value_objects/
│       └── image_source_type.dart
│
├── data/                          # Data layer implementation
│   ├── datasources/native/
│   │   └── ocr_native_datasource.dart  # MethodChannel bridge
│   ├── models/
│   │   ├── ocr_result_model.dart
│   │   ├── text_block_model.dart
│   │   └── bounding_box_model.dart
│   ├── mappers/
│   │   └── ocr_result_mapper.dart
│   └── repositories/
│       └── ocr_repository_impl.dart
│
├── application/                   # State management
│   └── ocr/logic/
│       ├── ocr_notifier.dart
│       ├── ocr_state.dart
│       └── ocr_providers.dart
│
├── presentation/                  # UI layer
│   ├── ocr/
│   │   ├── pages/ocr_page.dart
│   │   └── components/
│   │       ├── image_source_selector.dart
│   │       ├── ocr_result_view.dart
│   │       └── bounding_box_overlay.dart
│   └── shared/components/
│       ├── loading_indicator.dart
│       └── error_view.dart
│
└── main.dart

android/app/src/main/kotlin/.../
├── MainActivity.kt                # MethodChannel registration
└── ocr/
    ├── OcrMethodHandler.kt        # MethodChannel handler
    └── PaddleOcrEngine.kt         # Paddle Lite engine wrapper

ios/Runner/
├── AppDelegate.swift              # MethodChannel registration
├── OcrMethodHandler.swift         # MethodChannel handler
└── PaddleOcrEngine.swift          # Paddle Lite engine wrapper
```

## Dart API Usage

```dart
// The primary Dart API for triggering offline OCR:
Future<Either<Failure, OcrResult>> recognizeText(File image);

// Via the repository:
final repository = ref.read(ocrRepositoryProvider);
final result = await repository.recognizeText(imageFile);
result.fold(
  (failure) => print('Error: ${failure.message}'),
  (ocrResult) {
    print('Found ${ocrResult.blockCount} text blocks');
    print('Combined text: ${ocrResult.combinedText}');
    for (final block in ocrResult.textBlocks) {
      print('${block.text} (${block.confidence})');
    }
  },
);
```

## Model Configuration

Edit `lib/core/constants/app_constants.dart` to change model files, thread count, or detection thresholds:

```dart
static const String detModelFileName = 'ch_PP-OCRv3_det_slim_infer.nb';
static const String recModelFileName = 'ch_PP-OCRv3_rec_slim_infer.nb';
static const String clsModelFileName = 'ch_ppocr_mobile_v2.0_cls_slim_opt.nb';
static const int defaultThreadCount = 4;
static const int defaultMaxSideLen = 960;
```

## ML Kit Text Recognition v2

The app includes **ML Kit** as an alternative engine. It works offline and requires no model download.

- **Supported:** Latin, Chinese, Devanagari, Japanese, Korean
- **Not supported:** Arabic — use PaddleOCR for Arabic
- **Docs:** [ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2)

The `google_mlkit_text_recognition` package is included. For additional scripts (e.g. Chinese), add the corresponding pods (iOS) or Gradle dependencies (Android) per the package readme.

## References

- [PaddleOCR GitHub](https://github.com/PaddlePaddle/PaddleOCR)
- [Paddle Lite](https://github.com/PaddlePaddle/Paddle-Lite)
- [Paddle-Lite-Demo (Android/iOS OCR)](https://github.com/PaddlePaddle/Paddle-Lite-Demo)
- [ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2)
- [PP-OCRv3 Technical Report](https://arxiv.org/abs/2206.03001)
