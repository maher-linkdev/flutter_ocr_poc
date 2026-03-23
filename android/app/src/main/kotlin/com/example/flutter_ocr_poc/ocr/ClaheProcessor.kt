package com.example.flutter_ocr_poc.ocr

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * CLAHE (Contrast Limited Adaptive Histogram Equalization) — pure Kotlin, no OpenCV.
 *
 * Divides the image into tiles, computes per-tile histograms with clip limiting,
 * and uses bilinear interpolation between tile mappings for smooth results.
 * Much better than global contrast stretch for documents with uneven lighting.
 *
 * Operates on the luminance channel only, preserving color information.
 */
class ClaheProcessor(
    private val clipLimit: Double = 2.0,
    private val tilesX: Int = 4,
    private val tilesY: Int = 8
) {
    companion object {
        private const val HIST_SIZE = 256
    }

    /**
     * Apply CLAHE to a bitmap. Returns a new bitmap with enhanced contrast.
     * The input bitmap is not modified.
     */
    fun process(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width < tilesX * 2 || height < tilesY * 2) {
            // Image too small for tiling — return as-is
            return bitmap
        }

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Extract luminance channel
        val lum = IntArray(pixels.size)
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            lum[i] = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(0, 255)
        }

        // Compute tile dimensions
        val tileW = width / tilesX
        val tileH = height / tilesY

        // Compute clip limit in absolute pixel count per histogram bin
        val tilePixels = tileW * tileH
        val actualClipLimit = if (clipLimit <= 0) {
            Int.MAX_VALUE // no clipping
        } else {
            max(1, (clipLimit * tilePixels / HIST_SIZE).roundToInt())
        }

        // Build per-tile CDF lookup tables
        // cdfs[ty][tx] = mapping array of size 256
        val cdfs = Array(tilesY) { ty ->
            Array(tilesX) { tx ->
                computeTileCdf(lum, width, height, tx * tileW, ty * tileH, tileW, tileH, actualClipLimit)
            }
        }

        // Apply CLAHE with bilinear interpolation
        val result = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val lumVal = lum[idx]

                // Find which tile centers this pixel is between
                // Tile center coordinates
                val fx = (x.toFloat() / tileW) - 0.5f
                val fy = (y.toFloat() / tileH) - 0.5f

                val tx1 = fx.toInt().coerceIn(0, tilesX - 1)
                val ty1 = fy.toInt().coerceIn(0, tilesY - 1)
                val tx2 = (tx1 + 1).coerceAtMost(tilesX - 1)
                val ty2 = (ty1 + 1).coerceAtMost(tilesY - 1)

                // Interpolation weights
                val xa = (fx - tx1).coerceIn(0f, 1f)
                val ya = (fy - ty1).coerceIn(0f, 1f)

                // Bilinear interpolation of the four surrounding tile mappings
                val tl = cdfs[ty1][tx1][lumVal]
                val tr = cdfs[ty1][tx2][lumVal]
                val bl = cdfs[ty2][tx1][lumVal]
                val br = cdfs[ty2][tx2][lumVal]

                val top = tl * (1f - xa) + tr * xa
                val bot = bl * (1f - xa) + br * xa
                val newLum = (top * (1f - ya) + bot * ya).roundToInt().coerceIn(0, 255)

                // Scale original RGB channels by the luminance ratio
                val origLum = lum[idx]
                if (origLum == 0) {
                    result[idx] = (pixels[idx] and 0xFF000000.toInt()) or
                            (newLum shl 16) or (newLum shl 8) or newLum
                } else {
                    val scale = newLum.toFloat() / origLum
                    val r = min(255f, ((pixels[idx] shr 16) and 0xFF) * scale).roundToInt()
                        .coerceIn(0, 255)
                    val g = min(255f, ((pixels[idx] shr 8) and 0xFF) * scale).roundToInt()
                        .coerceIn(0, 255)
                    val b = min(255f, (pixels[idx] and 0xFF) * scale).roundToInt()
                        .coerceIn(0, 255)
                    result[idx] = (pixels[idx] and 0xFF000000.toInt()) or
                            (r shl 16) or (g shl 8) or b
                }
            }
        }

        val output = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Compute the CDF mapping table for one tile with histogram clipping.
     */
    private fun computeTileCdf(
        lum: IntArray,
        imgWidth: Int,
        imgHeight: Int,
        startX: Int,
        startY: Int,
        tileW: Int,
        tileH: Int,
        clipLimit: Int
    ): IntArray {
        val hist = IntArray(HIST_SIZE)

        // Build histogram for this tile
        val endX = min(startX + tileW, imgWidth)
        val endY = min(startY + tileH, imgHeight)
        var pixelCount = 0

        for (y in startY until endY) {
            for (x in startX until endX) {
                hist[lum[y * imgWidth + x]]++
                pixelCount++
            }
        }

        if (pixelCount == 0) return IntArray(HIST_SIZE) { it }

        // Clip histogram and redistribute excess
        var excess = 0
        for (i in 0 until HIST_SIZE) {
            if (hist[i] > clipLimit) {
                excess += hist[i] - clipLimit
                hist[i] = clipLimit
            }
        }

        // Distribute excess evenly across all bins
        val perBin = excess / HIST_SIZE
        val remainder = excess % HIST_SIZE
        for (i in 0 until HIST_SIZE) {
            hist[i] += perBin
            if (i < remainder) hist[i]++
        }

        // Build CDF
        val cdf = IntArray(HIST_SIZE)
        cdf[0] = hist[0]
        for (i in 1 until HIST_SIZE) {
            cdf[i] = cdf[i - 1] + hist[i]
        }

        // Normalize CDF to [0, 255]
        val cdfMin = cdf.first { it > 0 }
        val totalPixels = cdf[HIST_SIZE - 1]
        val mapping = IntArray(HIST_SIZE)

        if (totalPixels == cdfMin) {
            // All pixels have the same value
            for (i in 0 until HIST_SIZE) mapping[i] = i
        } else {
            for (i in 0 until HIST_SIZE) {
                mapping[i] = ((cdf[i] - cdfMin).toLong() * 255 / (totalPixels - cdfMin)).toInt()
                    .coerceIn(0, 255)
            }
        }

        return mapping
    }
}
