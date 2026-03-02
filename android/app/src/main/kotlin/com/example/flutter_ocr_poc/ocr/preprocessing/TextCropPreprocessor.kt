package com.example.flutter_ocr_poc.ocr.preprocessing

import android.graphics.Bitmap
import android.util.Log

/**
 * Orchestrator for the text crop preprocessing pipeline.
 *
 * Chains: Bilateral filter → Unsharp mask → Brightness normalization → Super-resolution (if small).
 * Each stage is independently toggleable via [PreprocessConfig].
 *
 * Manages bitmap lifecycle: recycles intermediates, never recycles the caller's original bitmap.
 */
class TextCropPreprocessor(private val config: PreprocessConfig) {

    companion object {
        private const val TAG = "TextCropPreprocessor"
    }

    private var bilateralFilter: BilateralFilter? = null
    private var unsharpMask: UnsharpMask? = null
    private var brightnessNormalizer: BrightnessNormalizer? = null
    private var superResRunner: SuperResOnnxRunner? = null

    fun initialize() {
        if (config.enableBilateralFilter) {
            bilateralFilter = BilateralFilter(
                radius = config.bilateralRadius,
                sigmaColor = config.bilateralSigmaColor,
                sigmaSpace = config.bilateralSigmaSpace
            )
        }
        if (config.enableUnsharpMask) {
            unsharpMask = UnsharpMask(amount = config.unsharpAmount)
        }
        if (config.enableBrightnessNorm) {
            brightnessNormalizer = BrightnessNormalizer(
                targetMean = config.brightnessTargetMean,
                skipRange = config.brightnessSkipRange
            )
        }
        if (config.enableSuperRes && config.superResModelPath != null) {
            try {
                superResRunner = SuperResOnnxRunner(config.superResModelPath)
                Log.i(TAG, "Super-resolution model loaded: ${config.superResModelPath}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load super-resolution model, SR disabled: ${e.message}")
            }
        }

        Log.i(TAG, "Preprocessing pipeline initialized — " +
                "bilateral=${config.enableBilateralFilter}, " +
                "unsharp=${config.enableUnsharpMask}, " +
                "brightness=${config.enableBrightnessNorm}, " +
                "superRes=${config.enableSuperRes && superResRunner != null}")
    }

    /**
     * Process a text crop through the pipeline.
     * Returns a new (or same) bitmap. The caller's original bitmap is never recycled.
     */
    fun process(bitmap: Bitmap): Bitmap {
        var current = bitmap
        val totalStart = System.currentTimeMillis()

        // Stage 1: Bilateral filter (denoise)
        if (bilateralFilter != null) {
            val t = System.currentTimeMillis()
            val next = bilateralFilter!!.process(current)
            Log.d(TAG, "Bilateral filter: ${System.currentTimeMillis() - t}ms")
            if (next !== current && current !== bitmap) current.recycle()
            current = next
        }

        // Stage 2: Unsharp mask (sharpen)
        if (unsharpMask != null) {
            val t = System.currentTimeMillis()
            val next = unsharpMask!!.process(current)
            Log.d(TAG, "Unsharp mask: ${System.currentTimeMillis() - t}ms")
            if (next !== current && current !== bitmap) current.recycle()
            current = next
        }

        // Stage 3: Brightness normalization
        if (brightnessNormalizer != null) {
            val t = System.currentTimeMillis()
            val next = brightnessNormalizer!!.process(current)
            Log.d(TAG, "Brightness norm: ${System.currentTimeMillis() - t}ms " +
                    if (next === current) "(skipped — already in range)" else "")
            if (next !== current && current !== bitmap) current.recycle()
            current = next
        }

        // Stage 4: Super-resolution (only for small crops)
        if (superResRunner != null && current.height < config.superResMaxHeight) {
            val t = System.currentTimeMillis()
            val next = superResRunner!!.process(current)
            Log.d(TAG, "Super-res 2x: ${current.width}x${current.height} → " +
                    "${next.width}x${next.height}, ${System.currentTimeMillis() - t}ms")
            if (current !== bitmap) current.recycle()
            current = next
        }

        Log.d(TAG, "Total preprocessing: ${System.currentTimeMillis() - totalStart}ms")
        return current
    }

    /**
     * Release all resources.
     */
    fun close() {
        superResRunner?.close()
        superResRunner = null
        bilateralFilter = null
        unsharpMask = null
        brightnessNormalizer = null
        Log.i(TAG, "Preprocessing pipeline released")
    }
}
