# Arabic Offline OCR — Options Summary

You want **Arabic offline**. The multilingual PP-OCRv5 rec model gives poor results on Arabic. Here's what we tried and what you can do.

---

## ✅ New technique: PIR → ONNX → ONNX Runtime (implemented)

**The app now supports the new ONNX path** for recognition. Instead of converting PIR to legacy `.nb` for Paddle Lite:

1. **Convert PIR to ONNX** using `paddle2onnx` (accepts `inference.json` + `inference.pdiparams`)
2. **Use ONNX Runtime** on Android for the rec model instead of Paddle Lite

This avoids the need for legacy `.pdmodel` or `.nb` for the Arabic rec model.

### How to enable Arabic rec with ONNX

1. **Convert the Arabic model to ONNX:**
   ```bash
   python scripts/convert_arabic_to_onnx.py
   ```
   Output: `scripts/arabic_rec_onnx/arabic_PP-OCRv5_mobile_rec.onnx`

2. **Copy to assets:**
   ```bash
   cp scripts/arabic_rec_onnx/arabic_PP-OCRv5_mobile_rec.onnx assets/models/
   ```

3. **Update `lib/core/constants/app_constants.dart`:**
   ```dart
   static const String? recOnnxFileName = 'arabic_PP-OCRv5_mobile_rec.onnx';
   static const String labelFileName = 'ppocr_keys_arabic.txt';
   ```

4. **Build and run.** Detection and cls stay on Paddle Lite; recognition uses ONNX Runtime with the Arabic model.

### Conversion (manual, if script fails)

```bash
# Requires paddle2onnx (pip install paddle2onnx paddlepaddle)
paddle2onnx --model_dir ~/.paddlex/official_models/arabic_PP-OCRv5_mobile_rec \
  --model_filename inference.json \
  --params_filename inference.pdiparams \
  --save_file scripts/arabic_rec_onnx/arabic_PP-OCRv5_mobile_rec.onnx \
  --opset_version 11
```

### App implementation

- **Paddle Lite** for det and cls (existing .nb).
- **ONNX Runtime** (`com.microsoft.onnxruntime:onnxruntime-android`) for rec when `recOnnxFileName` is set.
- `RecOnnxRunner.kt` runs the ONNX rec model; `PaddleOcrEngine` switches between Paddle Lite and ONNX based on init params.

### Alternative: Use mobile_ocr plugin

The [mobile_ocr](https://github.com/ente-io/mobile_ocr) Flutter plugin uses ONNX Runtime with PaddleOCR v5 models. You could switch to it and request or contribute Arabic rec model support.

---

## What We Tried

1. **Download Arabic model** — Got `inference.json` + `inference.pdiparams` from Hugging Face (PIR format).
2. **Convert to .nb** — Paddle Lite `opt` needs legacy `.pdmodel` + `.pdiparams`; it does **not** accept PIR/`inference.json`.
3. **PIR → legacy** — No simple automated path. PaddleOCR's `export_model.py` requires the **training checkpoint** (`.pdparams` from training), not the inference model. We couldn't find a public download for the Arabic v5 training checkpoint.
4. **paddle2onnx** — Would need `inference.json` as `inference.pdmodel`, but install failed (cmake/build deps). Could be retried in a proper env.
5. **PP-OCRv3 Arabic** — Same PIR format on Hugging Face; no `.pdmodel` there either.

---

## Practical Options

### Option A: Request Legacy / .nb from PaddleOCR (best if they respond)

Open an issue or discussion on [PaddleOCR GitHub](https://github.com/PaddlePaddle/PaddleOCR):

- Ask for **arabic_PP-OCRv5_mobile_rec** in legacy format (`.pdmodel` + `.pdiparams`) or pre-built Paddle Lite `.nb`.
- Cite deployment on Android with Paddle Lite.

### Option B: Export Yourself (requires setup)

1. Get the **training checkpoint** for Arabic v5 — PaddleOCR model zoo or aistudio may have it; check the [PP-OCRv5 multi-lang doc](https://www.paddleocr.ai/latest/en/version3.x/algorithm/PP-OCRv5/PP-OCRv5_multi_languages.html) for links.
2. Use a **Docker** image with Paddle 2.5 + PaddleOCR (or an env where `export_with_pir=False` works).
3. Run:
   ```bash
   export FLAGS_enable_pir_api=0
   python tools/export_model.py \
     -c configs/rec/PP-OCRv5/multi_language/arabic_PP-OCRv5_mobile_rec.yaml \
     -o Global.pretrained_model=/path/to/checkpoint \
        Global.save_inference_dir=./arabic_rec_export \
        Global.export_with_pir=False
   ```
4. Run `paddle_lite_opt` on the exported `inference.pdmodel` + `inference.pdiparams`.
5. Copy the `.nb` into `assets/models/` and point the app to it.

### Option C: Use Tesseract for Arabic (offline, different engine)

- **Tesseract** has Arabic support and runs fully offline.
- Integrate via `flutter_tesseract_ocr` or a similar plugin.
- Detection could stay as Paddle (boxes), recognition as Tesseract (Arabic).
- Different API and accuracy profile than PaddleOCR.

### Option D: Keep Multilingual v5 (temporary)

- Use `PP-OCRv5_mobile_rec.nb` + `ppocr_keys_ocrv5.txt`.
- Arabic will be less accurate; mainly for testing until Arabic .nb is available.

---

## Files in This Project

- `scripts/export_arabic_rec_to_nb.py` — Downloads Arabic model, tries conversion (currently blocked by PIR).
- `scripts/README_arabic_rec.md` — Export and opt steps.
- `assets/labels/ppocr_keys_arabic.txt` — Ready for when you have Arabic rec .nb.

---

## Bottom Line

**There is no one-click Arabic .nb today.** The Arabic model is only published in PIR format. To get Arabic offline with Paddle Lite you need either:

- Legacy export from PaddleOCR (training checkpoint + export script), or  
- A different engine (e.g. Tesseract) for recognition.

Raising an issue with PaddleOCR for legacy / .nb is the most direct path for your use case.
