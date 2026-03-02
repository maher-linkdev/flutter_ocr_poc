#!/usr/bin/env python3
"""
Extract the Arabic character dictionary from the HuggingFace PP-OCRv5 Arabic rec model.

Downloads config.json, extracts the character list, and writes it to a dictionary file.
Validates that num_classes matches dict_size + 2 (blank + space).

Usage (from repo root):
    pip install huggingface_hub
    python scripts/extract_arabic_dict.py

Output:
    assets/labels/ppocr_keys_arabic_v5.txt
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
WORK_DIR = REPO_ROOT / "scripts" / "arabic_rec_onnx"
LABELS_DIR = REPO_ROOT / "assets" / "labels"
HF_REPO = "PaddlePaddle/arabic_PP-OCRv5_mobile_rec"
OUTPUT_FILE = LABELS_DIR / "ppocr_keys_arabic_v5.txt"


def download_config() -> dict:
    """Download and parse config.json from HuggingFace."""
    try:
        from huggingface_hub import hf_hub_download
    except ImportError:
        print("Install: pip install huggingface_hub")
        sys.exit(1)

    os.makedirs(WORK_DIR, exist_ok=True)
    config_path = hf_hub_download(repo_id=HF_REPO, filename="config.json", local_dir=str(WORK_DIR))
    print(f"Downloaded config.json -> {config_path}")

    with open(config_path, "r", encoding="utf-8") as f:
        return json.load(f)


def extract_character_list(config: dict) -> list[str]:
    """Extract character list from the model config."""
    # PP-OCRv5 config structure varies; try common paths
    char_list = None

    # Path 1: PostProcess.character_dict_path or character_list
    postprocess = config.get("PostProcess", {})
    if "character_list" in postprocess:
        char_list = postprocess["character_list"]
    elif "character_dict_path" in postprocess:
        print(f"Config references external dict: {postprocess['character_dict_path']}")
        print("Will try to download it...")

    # Path 2: Global.character_dict_path
    global_cfg = config.get("Global", {})
    dict_path = global_cfg.get("character_dict_path", "")

    if char_list is None and dict_path:
        # Try downloading the dict from PaddleOCR repo
        print(f"Character dict path from config: {dict_path}")
        char_list = try_download_dict_from_paddleocr(dict_path)

    if char_list is None:
        # Fallback: try to find it in the HF repo
        char_list = try_download_dict_from_hf()

    if char_list is None:
        print("Could not extract character list from config.")
        print("Config keys:", list(config.keys()))
        if "PostProcess" in config:
            print("PostProcess keys:", list(config["PostProcess"].keys()))
        sys.exit(1)

    return char_list


def try_download_dict_from_paddleocr(dict_path: str) -> list[str] | None:
    """Try downloading the dict file from the PaddleOCR GitHub repo."""
    # dict_path is typically like 'ppocr/utils/dict/arabic_dict.txt'
    # or 'ppocr/utils/dict/ppocrv5_arabic_dict.txt'
    import urllib.request

    # Try PaddleOCR main branch
    base_url = "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/"
    url = base_url + dict_path
    print(f"Trying to download dict from: {url}")
    try:
        with urllib.request.urlopen(url) as resp:
            content = resp.read().decode("utf-8")
            chars = [line.strip() for line in content.splitlines() if line.strip()]
            if chars:
                print(f"Downloaded {len(chars)} characters from PaddleOCR repo")
                return chars
    except Exception as e:
        print(f"Could not download from PaddleOCR repo: {e}")

    return None


def try_download_dict_from_hf() -> list[str] | None:
    """Try downloading dict files from the HF model repo."""
    from huggingface_hub import hf_hub_download

    for dict_name in [
        "ppocrv5_arabic_dict.txt",
        "arabic_dict.txt",
        "ppocr_keys_arabic.txt",
    ]:
        try:
            path = hf_hub_download(repo_id=HF_REPO, filename=dict_name, local_dir=str(WORK_DIR))
            with open(path, "r", encoding="utf-8") as f:
                chars = [line.strip() for line in f if line.strip()]
            if chars:
                print(f"Found {dict_name} with {len(chars)} characters")
                return chars
        except Exception:
            continue

    return None


def validate_dict(chars: list[str], config: dict) -> None:
    """Validate dict size matches model's expected num_classes."""
    # num_classes in ONNX output = dict_size + 2 (blank at index 0, space at end)
    expected_classes = len(chars) + 2
    print(f"Dictionary size: {len(chars)} characters")
    print(f"Expected num_classes (dict + blank + space): {expected_classes}")

    # Try to find num_classes in config
    head_cfg = config.get("Head", {})
    if "out_channels" in head_cfg:
        model_classes = head_cfg["out_channels"]
        print(f"Model out_channels from config: {model_classes}")
        if model_classes != expected_classes:
            print(f"WARNING: Mismatch! Model expects {model_classes} but dict gives {expected_classes}")
            print("The dictionary may need adjustment.")
        else:
            print("Dictionary size matches model output channels.")


def write_dict(chars: list[str]) -> None:
    """Write character dictionary file (one char per line, no blank/space tokens)."""
    os.makedirs(LABELS_DIR, exist_ok=True)
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        for ch in chars:
            f.write(ch + "\n")
    print(f"Wrote {len(chars)} characters to {OUTPUT_FILE}")


def main() -> None:
    print("=== Extract Arabic PP-OCRv5 Character Dictionary ===\n")

    print("Step 1: Download config from HuggingFace...")
    config = download_config()

    print("\nStep 2: Extract character list...")
    chars = extract_character_list(config)

    print(f"\nStep 3: Validate (found {len(chars)} chars)...")
    validate_dict(chars, config)

    print("\nStep 4: Write dictionary file...")
    write_dict(chars)

    print(f"\nDone! Dictionary at: {OUTPUT_FILE}")
    print(f"Characters: {len(chars)}")
    # Show first/last few
    preview = chars[:5] + ["..."] + chars[-5:] if len(chars) > 10 else chars
    print(f"Preview: {preview}")


if __name__ == "__main__":
    main()
