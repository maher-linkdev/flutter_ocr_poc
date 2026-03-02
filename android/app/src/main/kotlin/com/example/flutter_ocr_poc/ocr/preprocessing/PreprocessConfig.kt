package com.example.flutter_ocr_poc.ocr.preprocessing

/**
 * Configuration for the text crop preprocessing pipeline.
 * Each stage can be independently toggled.
 */
data class PreprocessConfig(
    /** Enable bilateral filter (edge-preserving denoise). */
    val enableBilateralFilter: Boolean = true,
    /** Bilateral filter kernel radius (pixels). 2 → 5x5 kernel. */
    val bilateralRadius: Int = 2,
    /** Bilateral color-space sigma (higher = more color mixing). */
    val bilateralSigmaColor: Double = 75.0,
    /** Bilateral spatial sigma (higher = wider spatial influence). */
    val bilateralSigmaSpace: Double = 75.0,

    /** Enable unsharp mask sharpening. */
    val enableUnsharpMask: Boolean = true,
    /** Unsharp mask sharpening amount (1.0 = moderate, 2.0 = aggressive). */
    val unsharpAmount: Float = 1.5f,

    /** Enable brightness normalization. */
    val enableBrightnessNorm: Boolean = true,
    /** Target mean luminance (0–255). */
    val brightnessTargetMean: Float = 127f,
    /** Skip normalization if current mean is within this range of target. */
    val brightnessSkipRange: Float = 30f,

    /** Enable ESPCN 2x super-resolution for small crops. */
    val enableSuperRes: Boolean = true,
    /** Apply SR when crop height is below this threshold (pixels). */
    val superResMaxHeight: Int = 40,
    /** Path to the ESPCN ONNX model (set at runtime). */
    val superResModelPath: String? = null
)
