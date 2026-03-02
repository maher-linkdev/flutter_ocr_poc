#!/usr/bin/env python3
"""
Export PyTorch ESPCN (2×) to ONNX with dynamic spatial dimensions.

Usage:
    pip install torch onnx
    python scripts/export_espcn_onnx.py

Output: assets/models/espcn_x2.onnx (~100 KB)
"""

import torch
import torch.nn as nn
import os


class ESPCN(nn.Module):
    """Efficient Sub-Pixel Convolutional Neural Network (2× upscale).

    Lightweight architecture for real-time super-resolution.
    Operates on a single channel (Y in YCbCr).
    """

    def __init__(self, scale_factor: int = 2):
        super().__init__()
        self.net = nn.Sequential(
            nn.Conv2d(1, 64, kernel_size=5, padding=2),
            nn.ReLU(inplace=True),
            nn.Conv2d(64, 32, kernel_size=3, padding=1),
            nn.ReLU(inplace=True),
            nn.Conv2d(32, scale_factor ** 2, kernel_size=3, padding=1),
            nn.PixelShuffle(scale_factor),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return torch.clamp(self.net(x), 0.0, 1.0)


def main():
    model = ESPCN(scale_factor=2)
    model.eval()

    # Use pretrained weights if available, otherwise export with random init
    # (user should replace with trained weights for production use)
    weights_path = os.path.join(os.path.dirname(__file__), "espcn_x2.pth")
    if os.path.exists(weights_path):
        model.load_state_dict(torch.load(weights_path, map_location="cpu"))
        print(f"Loaded weights from {weights_path}")
    else:
        print("WARNING: No pretrained weights found. Exporting with random init.")
        print(f"  Place trained weights at: {weights_path}")

    # Dynamic spatial dims so the model works with any crop size
    dummy_input = torch.randn(1, 1, 24, 80)

    output_path = os.path.join(
        os.path.dirname(__file__), "..", "assets", "models", "espcn_x2.onnx"
    )
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    torch.onnx.export(
        model,
        dummy_input,
        output_path,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={
            "input": {0: "batch", 2: "height", 3: "width"},
            "output": {0: "batch", 2: "height_x2", 3: "width_x2"},
        },
        opset_version=11,
    )

    size_kb = os.path.getsize(output_path) / 1024
    print(f"Exported ONNX model: {output_path} ({size_kb:.1f} KB)")


if __name__ == "__main__":
    main()
