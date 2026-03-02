#!/usr/bin/env bash
# Re-download PP-OCRv5 .nb models from Paddle-Lite-Demo and copy into the app.
# Use this to get a clean set of .nb files that match the Paddle-Lite-Demo
# build (reduces version-mismatch crashes). Then use the SAME Paddle Lite
# Android libs as the demo (see SETUP.md).
#
# Usage:
#   ./reinstall_nb_models.sh           # Multilingual v5 (det + rec + cls)
#   ./reinstall_nb_models.sh arabic   # Same + try to build Arabic rec (main reason for many users)

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ASSETS_MODELS="$REPO_ROOT/assets/models"
ASSETS_LABELS="$REPO_ROOT/assets/labels"
WORK_DIR="$SCRIPT_DIR/.nb_download"
MODEL_NAME="PP-OCRv5_mobile"
WANT_ARABIC="${1:-}"

echo "Reinstalling .nb models (det + cls from demo; rec = ${WANT_ARABIC:+Arabic if build succeeds, else }multilingual)"
echo "  App assets: $ASSETS_MODELS"
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

# Same URLs as Paddle-Lite-Demo/ocr/assets/download.sh
BASE="https://paddlelite-demo.bj.bcebos.com"
DET_URL="$BASE/paddle-x/ocr/models/${MODEL_NAME}_det.tar.gz"
REC_URL="$BASE/paddle-x/ocr/models/${MODEL_NAME}_rec.tar.gz"
CLS_URL="$BASE/demo/ocr/models/ch_ppocr_mobile_v2.0_cls_slim_opt_for_cpu_v2_10_rc.tar.gz"

download_and_extract() {
  local url="$1"
  local name="$2"
  echo "Downloading $name..."
  curl -sL "$url" -o "${name}.tar.gz"
  tar -xzf "${name}.tar.gz"
  rm -f "${name}.tar.gz"
}

download_and_extract "$DET_URL" "det"
# Rec: only download multilingual if we are not trying Arabic (or Arabic build will run first)
if [ "$WANT_ARABIC" != "arabic" ]; then
  download_and_extract "$REC_URL" "rec"
fi
download_and_extract "$CLS_URL" "cls"

# If user asked for Arabic, try to build arabic_PP-OCRv5_mobile_rec.nb (no pre-built download exists)
if [ "$WANT_ARABIC" = "arabic" ]; then
  echo "--- Building Arabic rec .nb (no official pre-built download) ---"
  if [ -x "$SCRIPT_DIR/build_arabic_rec_nb.sh" ]; then
    "$SCRIPT_DIR/build_arabic_rec_nb.sh" || true
  else
    chmod +x "$SCRIPT_DIR/build_arabic_rec_nb.sh" 2>/dev/null || true
    if [ -x "$SCRIPT_DIR/build_arabic_rec_nb.sh" ]; then
      "$SCRIPT_DIR/build_arabic_rec_nb.sh" || true
    else
      echo "Run manually: ./scripts/build_arabic_rec_nb.sh"
      echo "Prerequisites: pip install paddlepaddle paddleocr paddlelite"
    fi
  fi
  # If Arabic .nb was not produced, fall back to multilingual rec so the app at least runs
  if [ ! -f "$ASSETS_MODELS/arabic_PP-OCRv5_mobile_rec.nb" ]; then
    echo "Arabic .nb not available. Downloading multilingual rec so the app can run."
    download_and_extract "$REC_URL" "rec"
    echo "For Arabic text you need to build arabic_PP-OCRv5_mobile_rec.nb (see scripts/README_arabic_rec.md)."
  fi
fi

# Copy all .nb into app assets (tarballs may extract to current dir or subdirs)
mkdir -p "$ASSETS_MODELS"
find "$WORK_DIR" -maxdepth 3 -name "*.nb" -exec cp -v {} "$ASSETS_MODELS/" \; 2>/dev/null || true
# If Python script already copied Arabic .nb, it's already in ASSETS_MODELS
if [ -f "$SCRIPT_DIR/arabic_rec_export/arabic_PP-OCRv5_mobile_rec.nb" ]; then
  cp -v "$SCRIPT_DIR/arabic_rec_export/arabic_PP-OCRv5_mobile_rec.nb" "$ASSETS_MODELS/" 2>/dev/null || true
fi

# Labels
mkdir -p "$ASSETS_LABELS"
if command -v curl >/dev/null 2>&1; then
  echo "Downloading labels..."
  curl -sL "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/ppocr_keys_ocrv5.txt" \
    -o "$ASSETS_LABELS/ppocr_keys_ocrv5.txt" 2>/dev/null || true
  curl -sL "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/arabic_dict.txt" \
    -o "$ASSETS_LABELS/ppocr_keys_arabic.txt" 2>/dev/null || true
  # ppocrv5_arabic_dict is REQUIRED for arabic_PP-OCRv5_mobile_rec (different from arabic_dict!)
  curl -sL "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/ppocrv5_arabic_dict.txt" \
    -o "$ASSETS_LABELS/ppocr_keys_arabic_v5.txt" 2>/dev/null || true
fi

echo "Done. Check: ls -la $ASSETS_MODELS"
if [ -f "$ASSETS_MODELS/arabic_PP-OCRv5_mobile_rec.nb" ]; then
  echo "Arabic rec installed. In app_constants.dart set recModelFileName = 'arabic_PP-OCRv5_mobile_rec.nb' and labelFileName = 'ppocr_keys_arabic_v5.txt'"
else
  echo "Using multilingual rec. For Arabic: see scripts/README_arabic_rec.md to build arabic_PP-OCRv5_mobile_rec.nb"
fi
echo "Use the SAME Paddle Lite Android libs as the demo (see SETUP.md) to avoid crashes."
