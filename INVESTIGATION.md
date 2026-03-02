# Arabic OCR Investigation — Full Findings & Action Plan

This document summarizes the deep investigation into installing and optimizing the **arabic_PP-OCRv5_mobile_rec** model for efficient Arabic text extraction.

---

## 1. Executive Summary

| Item | Finding |
|------|---------|
| **Current blocker** | App uses multilingual PP-OCRv5 rec model — not tuned for Arabic → poor Arabic results |
| **Solution** | Install **arabic_PP-OCRv5_mobile_rec.nb** (Arabic-specific, ~81% accuracy) |
| **Critical fix** | Use **ppocr_keys_arabic_v5.txt** (ppocrv5_arabic_dict), NOT ppocr_keys_arabic.txt |
| **No pre-built .nb** | Must build from pretrained checkpoint (Hugging Face model is PIR format) |

---

## 2. Model Availability

### Hugging Face (PaddlePaddle/arabic_PP-OCRv5_mobile_rec)
- **Format:** PIR (inference.json + inference.pdiparams) — **no .pdmodel**
- **Issue:** Paddle Lite `opt` requires legacy format (.pdmodel + .pdiparams)
- **Verdict:** Cannot convert directly; use checkpoint-based export instead

### Paddle Model Ecology (Recommended)
- **Pretrained checkpoint:**  
  `https://paddle-model-ecology.bj.bcebos.com/paddlex/official_pretrained_model/arabic_PP-OCRv5_mobile_rec_pretrained.pdparams`
- **Format:** .pdparams (training weights)
- **Verdict:** Use this with PaddleOCR `export_model.py` → legacy inference → `paddle_lite_opt` → .nb

### Paddle-Lite-Demo
- **Pre-built .nb:** Only PP-OCRv5_mobile (multilingual), **no Arabic-specific** model
- **URLs:** paddlelite-demo.bj.bcebos.com (det, rec, cls)

---

## 3. Label File — Critical Discovery

The **Arabic PP-OCRv5** model was trained with `ppocrv5_arabic_dict.txt`, **not** `arabic_dict.txt`.

| File | Characters | Use case |
|------|------------|----------|
| `arabic_dict.txt` | ~132 chars, simpler | PP-OCRv3 Arabic, older models |
| `ppocrv5_arabic_dict.txt` | ~700+ chars (Latin, symbols, Arabic) | **Arabic PP-OCRv5** — REQUIRED |

**If you use the wrong dictionary:** Class indices map to wrong characters → garbled output.

**Fix:** Use `ppocr_keys_arabic_v5.txt` (copy of ppocrv5_arabic_dict) when using arabic_PP-OCRv5_mobile_rec.nb.

---

## 4. How to Install Arabic Model (Step-by-Step)

### Option A: Automated Script (Recommended)

```bash
chmod +x scripts/build_arabic_rec_nb.sh
./scripts/build_arabic_rec_nb.sh
```

**Prerequisites:**
- Python 3.8+
- `pip install paddlepaddle paddleocr paddlelite`

**What it does:**
1. Downloads pretrained checkpoint from Paddle Model Ecology
2. Clones PaddleOCR, exports to legacy format (`export_with_pir=False`)
3. Runs `paddle_lite_opt` to produce .nb
4. Copies to `assets/models/arabic_PP-OCRv5_mobile_rec.nb`
5. Downloads `ppocr_keys_arabic_v5.txt` to `assets/labels/`

### Option B: reinstall_nb_models.sh

```bash
./scripts/reinstall_nb_models.sh arabic
```

This runs `build_arabic_rec_nb.sh` internally and falls back to multilingual rec if build fails.

### After Installation

In `lib/core/constants/app_constants.dart`:

```dart
static const String recModelFileName = 'arabic_PP-OCRv5_mobile_rec.nb';
static const String labelFileName = 'ppocr_keys_arabic_v5.txt';
```

---

## 5. Version Alignment (Avoid Crashes)

The `.nb` file must be produced by the **same** Paddle Lite **opt** version as the runtime in your app.

1. **Get Paddle Lite libs** from Paddle-Lite-Demo (branch `feature/paddle-x`): run `libs/download.sh`, copy generated libs to `android/app/src/main/jniLibs/`
2. **Use matching opt:** `pip install paddlelite` (or pin version to match demo)
3. If you get SIGSEGV or crashes when loading rec model → version mismatch

---

## 6. Optimizations for Better Arabic Results

### Implemented

1. **Recognition height 48** — PP-OCRv5 default (already in engine)
2. **RTL handling** — Auto-reverse Arabic text after CTC decode (already in engine)
3. **Detection thresholds** — Lowered for ID cards (DET_DB_THRESH 0.2, BOX 0.3, UNCLIP 1.8)
4. **Optional contrast enhancement** — `ENABLE_REC_CONTRAST_ENHANCE = true` in PaddleOcrEngine.kt for faded/low-contrast text

### Future Enhancements

| Enhancement | Difficulty | Impact | Notes |
|-------------|------------|--------|-------|
| Deskew / slant correction | Medium | High | Arabic cursive benefits from baseline correction |
| Adaptive binarization | Medium | Medium | Helps low-contrast docs |
| Box ordering RTL | Low | Medium | When combining blocks, order RTL for Arabic |
| Diacritic normalization | Low | Low | Optional post-process for matching |
| Two-pass (multilingual + Arabic) | Medium | Medium | Run both, pick best confidence per region |

---

## 7. ML Kit Text Recognition v2 (Alternative — No Arabic)

**Implemented in this app.** ML Kit v2 is on-device and offline ([docs](https://developers.google.com/ml-kit/vision/text-recognition/v2)).

| Script support | Latin, Chinese, Devanagari, Japanese, Korean |
|----------------|------------------------------------------------|
| **Arabic** | ❌ Not supported |
| Offline | ✅ Yes (bundled or via Play Services) |

Use **PaddleOCR** for Arabic; use **ML Kit** for Latin, Chinese, receipts, business cards.

## 8. Other Alternatives If Arabic .nb Build Fails

| Option | Notes |
|--------|-------|
| **Tesseract** | `ara` script, often worse than deep models |
| **Server-side Arabic OCR** | PaddleOCR or EasyOCR on server for critical fields |
| **Stay on multilingual v5** | Works but weaker Arabic; use `ppocr_keys_ocrv5.txt` |

---

## 9. References

| Resource | URL |
|----------|-----|
| Arabic PP-OCRv5 (HF) | https://huggingface.co/PaddlePaddle/arabic_PP-OCRv5_mobile_rec |
| Pretrained checkpoint | https://paddle-model-ecology.bj.bcebos.com/paddlex/official_pretrained_model/arabic_PP-OCRv5_mobile_rec_pretrained.pdparams |
| PaddleOCR arabic config | configs/rec/PP-OCRv5/multi_language/arabic_PP-OCRv5_mobile_rec.yaml |
| ppocrv5_arabic_dict | https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/utils/dict/ppocrv5_arabic_dict.txt |
| Paddle-Lite-Demo | https://github.com/PaddlePaddle/Paddle-Lite-Demo (branch feature/paddle-x) |
| PIR vs legacy (Discussion #15419) | https://github.com/PaddlePaddle/PaddleOCR/discussions/15419 |

---

## 10. Quick Checklist

- [ ] Run `./scripts/build_arabic_rec_nb.sh`
- [ ] Set `recModelFileName = 'arabic_PP-OCRv5_mobile_rec.nb'`
- [ ] Set `labelFileName = 'ppocr_keys_arabic_v5.txt'` (NOT ppocr_keys_arabic.txt)
- [ ] Ensure `ppocr_keys_arabic_v5.txt` exists in `assets/labels/`
- [ ] Align Paddle Lite opt version with app's jniLibs
- [ ] Optionally set `ENABLE_REC_CONTRAST_ENHANCE = true` for faded IDs
