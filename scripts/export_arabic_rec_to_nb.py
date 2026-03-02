#!/usr/bin/env python3
"""
Try to build arabic_PP-OCRv5_mobile_rec.nb from the Hugging Face inference model.

The HF model is in PIR format (inference.json + inference.pdiparams). Paddle Lite opt
needs legacy format (.pdmodel + .pdiparams). This script:
  1. Downloads the Arabic rec model from Hugging Face.
  2. Tries to convert to legacy and run paddle_lite_opt (if your Paddle supports it).
  3. Otherwise prints manual steps.

Usage (from repo root or scripts/):
  pip install paddlepaddle huggingface_hub paddlelite
  python scripts/export_arabic_rec_to_nb.py
"""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

# Default: run from project root; script may also be run from scripts/
REPO_ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = REPO_ROOT / "scripts" / "arabic_rec_export"
HF_REPO = "PaddlePaddle/arabic_PP-OCRv5_mobile_rec"


def download_from_hf() -> Path:
    try:
        from huggingface_hub import hf_hub_download
    except ImportError:
        print("Install: pip install huggingface_hub")
        sys.exit(1)
    os.makedirs(OUT_DIR, exist_ok=True)
    for name in ("inference.json", "inference.pdiparams"):
        path = hf_hub_download(repo_id=HF_REPO, filename=name, local_dir=str(OUT_DIR))
        print(f"Downloaded {name} -> {path}")
    return OUT_DIR


def try_convert_pir_to_legacy(work_dir: Path) -> bool:
    """If Paddle can load PIR and save as legacy, do it. Return True if we have .pdmodel."""
    pdmodel = work_dir / "inference.pdmodel"
    if pdmodel.exists():
        return True
    try:
        import paddle
    except ImportError:
        print("Paddle not installed; cannot try PIR->legacy conversion.")
        return False
    # Paddle 2.6+ may support loading PIR and saving with export_with_pir=False.
    # This is best-effort; if the API differs, we skip.
    try:
        # Some versions: load from directory with inference.json + pdiparams
        from paddle import base
        # Try loading inference model (path without extension)
        model_path = work_dir / "inference"
        if not (work_dir / "inference.json").exists():
            return False
        # paddle.load can load params; structure is in .json (PIR). Saving as legacy
        # typically requires the model to be built as a Layer and state_dict loaded.
        # Without the exact Paddle API for PIR->legacy, we skip and tell the user.
        print("PIR->legacy conversion not automated in this script.")
        return False
    except Exception as e:
        print(f"Conversion attempt failed: {e}")
        return False


def run_opt(work_dir: Path) -> bool:
    pdmodel = work_dir / "inference.pdmodel"
    pdiparams = work_dir / "inference.pdiparams"
    if not pdmodel.exists():
        # Prefer .pdmodel; if we only have .pdiparams from HF, we need legacy export elsewhere
        pdiparams_hf = work_dir / "inference.pdiparams"
        if pdiparams_hf.exists():
            print("You have inference.pdiparams but no .pdmodel. Export from PaddleOCR with")
            print("  Global.export_with_pir=False  to get inference.pdmodel, then run:")
        else:
            print("Missing inference.pdmodel. Export the model to legacy format first.")
        return False
    if not pdiparams.exists():
        print("Missing inference.pdiparams")
        return False
    try:
        import paddlelite
    except ImportError:
        print("Install: pip install paddlelite")
        return False
    out_nb = work_dir / "arabic_PP-OCRv5_mobile_rec.nb"
    cmd = [
        "paddle_lite_opt",
        "--model_file", str(pdmodel),
        "--param_file", str(pdiparams),
        "--optimize_out", str(work_dir / "arabic_PP-OCRv5_mobile_rec"),
        "--valid_targets", "arm",
        "--optimize_out_type", "naive_buffer",
    ]
    print("Running:", " ".join(cmd))
    r = subprocess.run(cmd)
    if r.returncode != 0:
        print("paddle_lite_opt failed.")
        return False
    if out_nb.exists():
        print(f"Success: {out_nb}")
        return True
    # opt may add .nb to the optimize_out path
    alt = work_dir / "arabic_PP-OCRv5_mobile_rec.nb"
    if alt.exists():
        print(f"Success: {alt}")
        return True
    return False


def main() -> None:
    print("Downloading Arabic PP-OCRv5 rec from Hugging Face...")
    work_dir = download_from_hf()
    try_convert_pir_to_legacy(work_dir)
    if run_opt(work_dir):
        nb = work_dir / "arabic_PP-OCRv5_mobile_rec.nb"
        if nb.exists():
            dest = REPO_ROOT / "assets" / "models" / "arabic_PP-OCRv5_mobile_rec.nb"
            os.makedirs(dest.parent, exist_ok=True)
            shutil.copy(nb, dest)
            print(f"Copied to {dest}")
            print("In app_constants.dart set recModelFileName = 'arabic_PP-OCRv5_mobile_rec.nb'")
            print("and labelFileName = 'ppocr_keys_arabic.txt'")
    else:
        print("\nTo get .nb manually:")
        print("1. Export from PaddleOCR with config arabic_PP-OCRv5_mobile_rec.yaml and")
        print("   Global.export_with_pir=False, then run paddle_lite_opt on the output.")
        print("2. See scripts/README_arabic_rec.md for full steps.")


if __name__ == "__main__":
    main()
