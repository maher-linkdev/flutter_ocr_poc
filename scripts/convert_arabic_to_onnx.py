#!/usr/bin/env python3
"""
Convert Arabic PP-OCRv5 rec from PIR format (inference.json + inference.pdiparams)
to ONNX, so we can use ONNX Runtime instead of Paddle Lite for the rec model.

Prerequisites:
  brew install cmake
  pip install paddle2onnx paddlepaddle huggingface_hub

Usage:
  python scripts/convert_arabic_to_onnx.py

Output:
  scripts/arabic_rec_onnx/arabic_PP-OCRv5_mobile_rec.onnx
"""
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
MODEL_DIR = Path.home() / ".paddlex" / "official_models" / "arabic_PP-OCRv5_mobile_rec"
OUT_DIR = REPO_ROOT / "scripts" / "arabic_rec_onnx"
OUT_ONNX = OUT_DIR / "arabic_PP-OCRv5_mobile_rec.onnx"


def ensure_model() -> bool:
    """Ensure Arabic model is downloaded (via PaddleOCR load)."""
    if (MODEL_DIR / "inference.pdiparams").exists():
        return True
    try:
        from paddleocr import TextRecognition
        TextRecognition(model_name="arabic_PP-OCRv5_mobile_rec")
        return (MODEL_DIR / "inference.pdiparams").exists()
    except Exception as e:
        print(f"Download model first: {e}")
        return False


def convert_with_paddle2onnx() -> bool:
    """Run paddle2onnx CLI to convert PIR -> ONNX."""
    cmd = [
        "paddle2onnx",
        "--model_dir", str(MODEL_DIR),
        "--model_filename", "inference.json",
        "--params_filename", "inference.pdiparams",
        "--save_file", str(OUT_ONNX),
        "--opset_version", "11",
        "--enable_onnx_checker", "True",
    ]
    print("Running:", " ".join(cmd))
    r = subprocess.run(cmd)
    return r.returncode == 0 and OUT_ONNX.exists()


def convert_with_api() -> bool:
    """Fallback: use paddle2onnx Python API."""
    try:
        import paddle2onnx
    except ImportError:
        print("Install: pip install paddle2onnx (requires cmake for build)")
        return False
    os.makedirs(OUT_DIR, exist_ok=True)
    onnx_model, _ = paddle2onnx.export(
        str(MODEL_DIR),
        "inference.json",
        "inference.pdiparams",
        opset_version=11,
        enable_onnx_checker=True,
    )
    OUT_ONNX.write_bytes(onnx_model)
    print(f"Saved: {OUT_ONNX}")
    return True


def main() -> None:
    if not ensure_model():
        sys.exit(1)
    os.makedirs(OUT_DIR, exist_ok=True)
    if convert_with_paddle2onnx():
        print(f"\nSuccess: {OUT_ONNX}")
        print("Next: Add ONNX Runtime to Android and use this model for rec.")
    elif convert_with_api():
        print(f"\nSuccess: {OUT_ONNX}")
    else:
        print("\nConversion failed. Install cmake and paddle2onnx:")
        print("  brew install cmake")
        print("  pip install paddle2onnx")
        sys.exit(1)


if __name__ == "__main__":
    main()
