# Building arabic_PP-OCRv5_mobile_rec.nb for better Arabic text

Detection is ~90% accurate; recognition is poor because the app uses the **multilingual** PP-OCRv5 rec model, which is not tuned for Arabic. To get accurate Arabic text (e.g. on ID cards), you need the **Arabic-specific** recognition model in Paddle Lite `.nb` format.

## Quick start: Automated build

```bash
./scripts/build_arabic_rec_nb.sh
```

Prerequisites: `pip install paddlepaddle paddleocr paddlelite`

Then in `app_constants.dart` set:
- `recModelFileName = 'arabic_PP-OCRv5_mobile_rec.nb'`
- `labelFileName = 'ppocr_keys_arabic_v5.txt'` ← **Use ppocrv5_arabic_dict, NOT arabic_dict!**

## What we changed in the app

1. **Recognition input height**  
   The engine now uses **height 48** (PP-OCRv5 default) instead of 32. This can help a bit even with the multilingual model.

2. **Arabic model**  
   There is **no direct download** for `arabic_PP-OCRv5_mobile_rec.nb`. You must **export** the Arabic model and then **convert** it to `.nb` with Paddle Lite’s `opt` tool.

## Option A: Export from PaddleOCR (recommended if you have a checkpoint)

If you have (or can obtain) a **training checkpoint** for `arabic_PP-OCRv5_mobile_rec` (e.g. `.pdparams` from PaddleOCR or their model zoo):

1. Clone PaddleOCR and install deps:
   ```bash
   git clone https://github.com/PaddlePaddle/PaddleOCR.git
   cd PaddleOCR
   pip install -r requirements.txt
   pip install paddlepaddle  # or paddlepaddle-gpu
   ```

2. Export to **legacy** inference format (so Paddle Lite opt can read it):
   ```bash
   python tools/export_model.py \
     -c configs/rec/PP-OCRv5/multi_language/arabic_PP-OCRv5_mobile_rec.yaml \
     -o Global.pretrained_model=/path/to/arabic_rec_pretrained \
        Global.save_inference_dir=./arabic_rec_export \
        Global.export_with_pir=False
   ```
   This produces `arabic_rec_export/inference.pdmodel` and `inference.pdiparams`.

3. Convert to `.nb` (use the same Paddle Lite version as your app):
   ```bash
   pip install paddlelite
   paddle_lite_opt \
     --model_file=./arabic_rec_export/inference.pdmodel \
     --param_file=./arabic_rec_export/inference.pdiparams \
     --optimize_out=arabic_PP-OCRv5_mobile_rec \
     --valid_targets=arm \
     --optimize_out_type=naive_buffer
   ```

4. Copy the result into the Flutter app and point the app to it:
   ```bash
   cp arabic_PP-OCRv5_mobile_rec.nb /path/to/flutter_ocr_poc/assets/models/
   ```
   Download the correct label file (ppocrv5_arabic_dict):
   ```bash
   curl -sL "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/ppocrv5_arabic_dict.txt" \
     -o assets/labels/ppocr_keys_arabic_v5.txt
   ```
   In `lib/core/constants/app_constants.dart` set:
   - `recModelFileName = 'arabic_PP-OCRv5_mobile_rec.nb'`
   - `labelFileName = 'ppocr_keys_arabic_v5.txt'` (NOT ppocr_keys_arabic.txt!)

## Option B: Hugging Face model (inference format only)

The [Hugging Face model](https://huggingface.co/PaddlePaddle/arabic_PP-OCRv5_mobile_rec) is published in **Paddle inference format** as:
- `inference.json` (PIR program)
- `inference.pdiparams`

Paddle Lite’s `opt` tool only accepts **legacy** format (`.pdmodel` + `.pdiparams`). So you must:

1. **Convert PIR → legacy** using Paddle on your machine (e.g. load the model and save with `export_with_pir=False`), then run `paddle_lite_opt` as in Option A step 3, **or**
2. Ask PaddleOCR / PaddlePaddle for a **legacy** or **.nb** build of the Arabic v5 rec model (e.g. via an issue or model zoo).

The script `export_arabic_rec_to_nb.py` in this folder attempts to download the Hugging Face model and, if your Paddle version can load and re-save it in legacy form, runs the conversion. If it fails, use Option A with a checkpoint or the manual steps above.

## Label file

**CRITICAL:** The Arabic PP-OCRv5 model was trained with **ppocrv5_arabic_dict.txt** (different from arabic_dict.txt!).  
Use `ppocr_keys_arabic_v5.txt` (a copy of ppocrv5_arabic_dict) when using arabic_PP-OCRv5_mobile_rec.  
Do NOT use `ppocr_keys_arabic.txt` (arabic_dict) or `ppocr_keys_ocrv5.txt` (multilingual) with the Arabic model.
