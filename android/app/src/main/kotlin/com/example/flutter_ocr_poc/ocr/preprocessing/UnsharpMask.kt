package com.example.flutter_ocr_poc.ocr.preprocessing

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * Unsharp mask sharpening — pure Kotlin.
 *
 * Sharpens blurred text via: sharpened = original + amount × (original − blurred)
 * Uses a 3×3 Gaussian blur kernel. Operates per-channel on RGB.
 */
class UnsharpMask(
    private val amount: Float = 1.5f
) {
    companion object {
        // 3×3 Gaussian kernel (σ ≈ 0.85), sums to 16 for integer math
        private val KERNEL = intArrayOf(
            1, 2, 1,
            2, 4, 2,
            1, 2, 1
        )
        private const val KERNEL_SUM = 16
    }

    /**
     * Apply unsharp mask. Returns a new bitmap; input is not modified.
     */
    fun process(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val result = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val origPixel = pixels[idx]
                val origR = (origPixel shr 16) and 0xFF
                val origG = (origPixel shr 8) and 0xFF
                val origB = origPixel and 0xFF

                // 3×3 Gaussian blur
                var blurR = 0
                var blurG = 0
                var blurB = 0

                for (ky in -1..1) {
                    val ny = (y + ky).coerceIn(0, h - 1)
                    for (kx in -1..1) {
                        val nx = (x + kx).coerceIn(0, w - 1)
                        val nPixel = pixels[ny * w + nx]
                        val kw = KERNEL[(ky + 1) * 3 + (kx + 1)]

                        blurR += ((nPixel shr 16) and 0xFF) * kw
                        blurG += ((nPixel shr 8) and 0xFF) * kw
                        blurB += (nPixel and 0xFF) * kw
                    }
                }

                blurR /= KERNEL_SUM
                blurG /= KERNEL_SUM
                blurB /= KERNEL_SUM

                // Unsharp mask: sharpened = original + amount * (original - blurred)
                val outR = (origR + amount * (origR - blurR)).roundToInt().coerceIn(0, 255)
                val outG = (origG + amount * (origG - blurG)).roundToInt().coerceIn(0, 255)
                val outB = (origB + amount * (origB - blurB)).roundToInt().coerceIn(0, 255)

                result[idx] = (origPixel and 0xFF000000.toInt()) or
                        (outR shl 16) or (outG shl 8) or outB
            }
        }

        val output = Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }
}
