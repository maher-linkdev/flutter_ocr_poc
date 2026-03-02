#!/usr/bin/env bash
# Convert exported inference files to .nb (run on Linux - paddlelite has no macOS ARM wheels).
#
# Usage (on Linux, or in Docker/CI):
#   ./scripts/convert_exported_to_nb.sh
#
# Requires: scripts/arabic_rec_build/arabic_rec_export/inference.pdmodel and inference.pdiparams
# (Run build_arabic_rec_nb.sh first on Mac to get the export.)

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
EXPORT_DIR="$SCRIPT_DIR/arabic_rec_build/arabic_rec_export"
ASSETS_MODELS="$REPO_ROOT/assets/models"
ASSETS_LABELS="$REPO_ROOT/assets/labels"

if [ ! -f "$EXPORT_DIR/inference.pdmodel" ]; then
  echo "ERROR: Run ./scripts/build_arabic_rec_nb.sh first to export the model."
  exit 1
fi

echo "Converting to .nb..."
pip install -q paddlelite
cd "$EXPORT_DIR"
paddle_lite_opt \
  --model_file=inference.pdmodel \
  --param_file=inference.pdiparams \
  --optimize_out=arabic_PP-OCRv5_mobile_rec \
  --valid_targets=arm \
  --optimize_out_type=naive_buffer

mkdir -p "$ASSETS_MODELS"
cp -v arabic_PP-OCRv5_mobile_rec.nb "$ASSETS_MODELS/"
echo "Downloading ppocr_keys_arabic_v5.txt..."
curl -sL "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/ppocrv5_arabic_dict.txt" \
  -o "$ASSETS_LABELS/ppocr_keys_arabic_v5.txt" || true
echo ""
echo "Done! Set in app_constants.dart: recModelFileName='arabic_PP-OCRv5_mobile_rec.nb', labelFileName='ppocr_keys_arabic_v5.txt'"
