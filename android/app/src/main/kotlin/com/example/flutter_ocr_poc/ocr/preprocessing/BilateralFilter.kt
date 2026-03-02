package com.example.flutter_ocr_poc.ocr.preprocessing

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Edge-preserving bilateral filter — pure Kotlin, no OpenCV.
 *
 * Reduces noise while preserving text edges and thin diacritical marks.
 * Uses a square kernel with Gaussian weighting in both spatial and color domains.
 */
class BilateralFilter(
    private val radius: Int = 2,
    private val sigmaColor: Double = 75.0,
    private val sigmaSpace: Double = 75.0
) {
    // Pre-computed spatial Gaussian weights for the kernel
    private val spatialWeights: FloatArray
    private val kernelSize: Int = 2 * radius + 1

    // Pre-computed color Gaussian LUT (distance 0..441 for RGB euclidean max √(3×255²))
    private val colorWeightLut: FloatArray

    init {
        // Spatial weights
        val invSigmaSpace2 = -0.5 / (sigmaSpace * sigmaSpace)
        spatialWeights = FloatArray(kernelSize * kernelSize)
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val dist2 = (dx * dx + dy * dy).toDouble()
                spatialWeights[(dy + radius) * kernelSize + (dx + radius)] =
                    exp(dist2 * invSigmaSpace2).toFloat()
            }
        }

        // Color distance LUT — max squared distance for single-channel diff is 255²=65025
        // We use per-channel intensity difference, so LUT indexed by squared diff (0..65025)
        val maxDist2 = 255 * 255 + 1
        val invSigmaColor2 = -0.5 / (sigmaColor * sigmaColor)
        colorWeightLut = FloatArray(maxDist2)
        for (i in 0 until maxDist2) {
            colorWeightLut[i] = exp(i.toDouble() * invSigmaColor2).toFloat()
        }
    }

    /**
     * Apply bilateral filter. Returns a new bitmap; input is not modified.
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
                val centerPixel = pixels[idx]
                val cr = (centerPixel shr 16) and 0xFF
                val cg = (centerPixel shr 8) and 0xFF
                val cb = centerPixel and 0xFF

                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var sumWeight = 0f

                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) continue
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        if (nx < 0 || nx >= w) continue

                        val nIdx = ny * w + nx
                        val nPixel = pixels[nIdx]
                        val nr = (nPixel shr 16) and 0xFF
                        val ng = (nPixel shr 8) and 0xFF
                        val nb = nPixel and 0xFF

                        // Color distance (sum of squared per-channel diffs)
                        val dr = cr - nr
                        val dg = cg - ng
                        val db = cb - nb
                        val colorDist2 = dr * dr + dg * dg + db * db

                        // Combined weight = spatial × color
                        val spatialW = spatialWeights[(dy + radius) * kernelSize + (dx + radius)]
                        val colorW = if (colorDist2 < colorWeightLut.size) {
                            colorWeightLut[colorDist2]
                        } else {
                            0f
                        }
                        val weight = spatialW * colorW

                        sumR += nr * weight
                        sumG += ng * weight
                        sumB += nb * weight
                        sumWeight += weight
                    }
                }

                val outR = if (sumWeight > 0) (sumR / sumWeight).roundToInt().coerceIn(0, 255) else cr
                val outG = if (sumWeight > 0) (sumG / sumWeight).roundToInt().coerceIn(0, 255) else cg
                val outB = if (sumWeight > 0) (sumB / sumWeight).roundToInt().coerceIn(0, 255) else cb

                result[idx] = (centerPixel and 0xFF000000.toInt()) or
                        (outR shl 16) or (outG shl 8) or outB
            }
        }

        val output = Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }
}
