package com.example.flutter_ocr_poc.ocr.preprocessing

import android.graphics.Bitmap
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Brightness normalization — pure Kotlin.
 *
 * Scales RGB proportionally so that the mean luminance of the image
 * matches a target value. Skips if already within an acceptable range.
 * Same luminance-ratio technique used in ClaheProcessor (lines 103–117).
 */
class BrightnessNormalizer(
    private val targetMean: Float = 127f,
    private val skipRange: Float = 30f
) {
    /**
     * Apply brightness normalization. Returns a new bitmap if adjusted,
     * or the original bitmap (same reference) if skipped.
     */
    fun process(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Compute current mean luminance
        var lumSum = 0L
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            lumSum += (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()
        }

        val currentMean = lumSum.toFloat() / pixels.size

        // Skip if already close to target
        if (kotlin.math.abs(currentMean - targetMean) <= skipRange) {
            return bitmap
        }

        // Avoid division by zero for very dark images
        if (currentMean < 1f) {
            return bitmap
        }

        val scale = targetMean / currentMean

        val result = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = min(255f, ((pixel shr 16) and 0xFF) * scale).roundToInt().coerceIn(0, 255)
            val g = min(255f, ((pixel shr 8) and 0xFF) * scale).roundToInt().coerceIn(0, 255)
            val b = min(255f, (pixel and 0xFF) * scale).roundToInt().coerceIn(0, 255)
            result[i] = (pixel and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }

        val output = Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }
}
