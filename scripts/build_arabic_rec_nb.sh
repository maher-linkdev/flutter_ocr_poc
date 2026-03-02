#!/usr/bin/env bash
# Build arabic_PP-OCRv5_mobile_rec.nb from the pretrained checkpoint.
#
# This is the RECOMMENDED way to get the Arabic recognition model for Paddle Lite.
# The Hugging Face model is in PIR format (inference.json + pdiparams) which
# paddle_lite_opt cannot read. The pretrained checkpoint can be exported to
# legacy .pdmodel + .pdiparams, then converted to .nb.
#
# Prerequisites:
#   - Python 3.8+
#   - pip install paddlepaddle paddleocr paddlelite
#
# Usage:
#   ./scripts/build_arabic_rec_nb.sh
#
# Output:
#   assets/models/arabic_PP-OCRv5_mobile_rec.nb
#   assets/labels/ppocr_keys_arabic_v5.txt  (ppocrv5_arabic_dict - REQUIRED for this model)

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WORK_DIR="$SCRIPT_DIR/arabic_rec_build"
PRETRAINED_URL="https://paddle-model-ecology.bj.bcebos.com/paddlex/official_pretrained_model/arabic_PP-OCRv5_mobile_rec_pretrained.pdparams"
ASSETS_MODELS="$REPO_ROOT/assets/models"
ASSETS_LABELS="$REPO_ROOT/assets/labels"

echo "=== Building arabic_PP-OCRv5_mobile_rec.nb ==="
echo "Work dir: $WORK_DIR"
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

# Step 1: Download pretrained checkpoint
PRETRAINED_FILE="arabic_PP-OCRv5_mobile_rec_pretrained.pdparams"
if [ ! -f "$PRETRAINED_FILE" ]; then
  echo "Downloading pretrained model..."
  curl -sL "$PRETRAINED_URL" -o "$PRETRAINED_FILE" || {
    echo "ERROR: Failed to download. Check URL or try manual download:"
    echo "  $PRETRAINED_URL"
    exit 1
  }
fi
echo "Pretrained: $PRETRAINED_FILE ($(du -h "$PRETRAINED_FILE" | cut -f1))"

# Step 2: Clone PaddleOCR if not present (we need the config and export script)
PADDLEOCR_DIR="$WORK_DIR/PaddleOCR"
if [ ! -d "$PADDLEOCR_DIR" ]; then
  echo "Cloning PaddleOCR..."
  git clone --depth 1 https://github.com/PaddlePaddle/PaddleOCR.git "$PADDLEOCR_DIR"
fi

# Step 2b: Create venv with Python 3.10 (paddlelite needs 3.10, not 3.13)
VENV_DIR="$WORK_DIR/venv"
PYTHON_CMD=""
for py in python3.10 python3.11 python3.12 python3; do
  if command -v $py >/dev/null 2>&1 && $py -c "import sys; exit(0 if sys.version_info < (3, 13) else 1)" 2>/dev/null; then
    PYTHON_CMD=$py
    break
  fi
done
if [ -z "$PYTHON_CMD" ]; then
  echo "ERROR: Need Python 3.10–3.12 (paddlelite doesn't support 3.13). Install with: brew install python@3.10"
  exit 1
fi
echo "Using $PYTHON_CMD"
if [ ! -d "$VENV_DIR" ]; then
  echo "Creating virtual environment..."
  $PYTHON_CMD -m venv "$VENV_DIR"
fi
source "$VENV_DIR/bin/activate"
echo "Installing PaddleOCR dependencies (may take a few minutes)..."
# Use Paddle 2.6.x for export compatibility (3.x has export issues)
pip install -q -r "$PADDLEOCR_DIR/requirements.txt" "paddlepaddle>=2.5,<2.7" paddleocr 2>/dev/null || pip install -r "$PADDLEOCR_DIR/requirements.txt" "paddlepaddle>=2.5,<2.7" paddleocr
# paddlelite has no macOS ARM wheels - install if available (Linux only)
pip install paddlelite 2>/dev/null || echo "Note: paddlelite not available (macOS ARM). Run scripts/convert_exported_to_nb.sh on Linux after export."

cd "$PADDLEOCR_DIR"

# Step 3: Export (requires Paddle 2.6.x - 3.x has export compatibility issues) to legacy inference format (pdmodel + pdiparams)
EXPORT_DIR="$WORK_DIR/arabic_rec_export"
mkdir -p "$EXPORT_DIR"

# IMPORTANT: arabic_PP-OCRv5 uses ppocrv5_arabic_dict.txt (NOT arabic_dict.txt)
export FLAGS_enable_pir_api=0
echo "Exporting to legacy format (this may take a minute)..."
python tools/export_model.py \
  -c configs/rec/PP-OCRv5/multi_language/arabic_PP-OCRv5_mobile_rec.yaml \
  -o Global.pretrained_model="$WORK_DIR/arabic_PP-OCRv5_mobile_rec_pretrained" \
     Global.save_inference_dir="$EXPORT_DIR" \
     Global.export_with_pir=False

if [ ! -f "$EXPORT_DIR/inference.pdmodel" ]; then
  echo "ERROR: Export failed. inference.pdmodel not found."
  echo "Check that PaddleOCR config exists: configs/rec/PP-OCRv5/multi_language/arabic_PP-OCRv5_mobile_rec.yaml"
  exit 1
fi
echo "Exported: $EXPORT_DIR/inference.pdmodel, inference.pdiparams"

# Step 4: Convert to Paddle Lite .nb
# paddlelite has no macOS ARM wheels - use Docker on Mac, or run on Linux
NB_OUT="$WORK_DIR/arabic_PP-OCRv5_mobile_rec"
NB_FILE="${NB_OUT}.nb"
if command -v paddle_lite_opt >/dev/null 2>&1; then
  echo "Converting to .nb..."
  paddle_lite_opt \
    --model_file="$EXPORT_DIR/inference.pdmodel" \
    --param_file="$EXPORT_DIR/inference.pdiparams" \
    --optimize_out="$NB_OUT" \
    --valid_targets=arm \
    --optimize_out_type=naive_buffer
elif command -v docker >/dev/null 2>&1; then
  echo "paddle_lite_opt not found. Using Docker to convert..."
  docker run --rm -v "$EXPORT_DIR:/work" -w /work paddlepaddle/paddle:latest \
    sh -c "pip install -q paddlelite && paddle_lite_opt \
      --model_file=inference.pdmodel --param_file=inference.pdiparams \
      --optimize_out=arabic_PP-OCRv5_mobile_rec --valid_targets=arm \
      --optimize_out_type=naive_buffer"
  NB_FILE="$EXPORT_DIR/arabic_PP-OCRv5_mobile_rec.nb"
else
  echo ""
  echo "=== Export complete, conversion needs Linux/Docker ==="
  echo "Inference files: $EXPORT_DIR/inference.pdmodel, inference.pdiparams"
  echo ""
  echo "To get .nb, run on Linux or in Docker:"
  echo "  pip install paddlelite"
  echo "  paddle_lite_opt --model_file=$EXPORT_DIR/inference.pdmodel \\"
  echo "    --param_file=$EXPORT_DIR/inference.pdiparams \\"
  echo "    --optimize_out=arabic_PP-OCRv5_mobile_rec --valid_targets=arm \\"
  echo "    --optimize_out_type=naive_buffer"
  echo ""
  echo "Then copy arabic_PP-OCRv5_mobile_rec.nb to assets/models/"
  exit 0
fi

if [ ! -f "$NB_FILE" ]; then
  echo "ERROR: Conversion failed. Exported files in $EXPORT_DIR"
  exit 1
fi

# Step 5: Copy to app assets
mkdir -p "$ASSETS_MODELS"
cp -v "$NB_FILE" "$ASSETS_MODELS/arabic_PP-OCRv5_mobile_rec.nb"
echo "Installed: $ASSETS_MODELS/arabic_PP-OCRv5_mobile_rec.nb"

# Step 6: Download correct label file (ppocrv5_arabic_dict - REQUIRED for Arabic PP-OCRv5)
# The Arabic PP-OCRv5 model was trained with ppocrv5_arabic_dict.txt, NOT arabic_dict.txt!
mkdir -p "$ASSETS_LABELS"
echo "Downloading ppocrv5_arabic_dict (correct for Arabic PP-OCRv5)..."
curl -sL "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/ppocrv5_arabic_dict.txt" \
  -o "$ASSETS_LABELS/ppocr_keys_arabic_v5.txt" || {
  echo "WARN: Could not download. Get it manually and save as assets/labels/ppocr_keys_arabic_v5.txt"
}

echo ""
echo "=== SUCCESS ==="
echo "1. In lib/core/constants/app_constants.dart set:"
echo "   recModelFileName = 'arabic_PP-OCRv5_mobile_rec.nb'"
echo "   labelFileName = 'ppocr_keys_arabic_v5.txt'"
echo ""
echo "2. CRITICAL: Use ppocr_keys_arabic_v5.txt (ppocrv5_arabic_dict), NOT ppocr_keys_arabic.txt (arabic_dict)."
echo "   The Arabic PP-OCRv5 model expects the ppocrv5 dictionary order."
echo ""
echo "3. Ensure Paddle Lite libs match the opt version used here (see SETUP.md)."
