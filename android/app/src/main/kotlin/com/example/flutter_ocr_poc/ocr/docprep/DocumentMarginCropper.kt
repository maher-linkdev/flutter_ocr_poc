package com.example.flutter_ocr_poc.ocr.docprep

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Removes large background margins (table, desk) around a document before UVDoc unwarp.
 *
 * UVDoc resizes the whole image to a fixed tensor size; if the document is small in the frame,
 * unwarp cannot "zoom in" and excess top/bottom (or left/right) remains. Ink/Otsu-based trimming
 * fails on full frames because texture (wood grain, shadows) is classified as foreground.
 *
 * This uses **gradient energy** (edge strength) per row and per column on a downscaled copy:
 * document regions have stronger, more clustered edges than mostly uniform margins. Profiles are
 * smoothed and thresholded robustly so vertical and horizontal margins are treated symmetrically.
 */
object DocumentMarginCropper {

    private const val TAG = "DocumentMarginCropper"

    /** Longer side of the analysis bitmap (keeps CPU down on 12MP photos). */
    private const val ANALYSIS_MAX_SIDE = 720

    private const val SMOOTH_RADIUS = 6
    private const val MIN_CONTENT_RUN = 12
    private const val FRACTION_OF_PEAK = 0.12f
    private const val MIN_TRIM_FRACTION = 0.04f
    /** Allow strong crops when the document is small in frame (large table margins). */
    private const val MAX_TRIM_FRACTION = 0.78f

    /**
     * If margins are large enough, returns a new cropped bitmap (with [paddingPx]).
     * Otherwise returns [bitmap] unchanged (caller must not recycle).
     */
    fun cropIfNeeded(bitmap: Bitmap, paddingPx: Int = 20): Bitmap {
        val bounds = estimateBounds(bitmap) ?: return bitmap
        val left = max(0, bounds.left - paddingPx)
        val top = max(0, bounds.top - paddingPx)
        val right = min(bitmap.width, bounds.right + paddingPx)
        val bottom = min(bitmap.height, bounds.bottom + paddingPx)
        val cw = right - left
        val ch = bottom - top
        if (cw < 32 || ch < 32) return bitmap

        val removedH = (bitmap.height - ch).toFloat() / bitmap.height
        val removedW = (bitmap.width - cw).toFloat() / bitmap.width
        if (removedH < MIN_TRIM_FRACTION && removedW < MIN_TRIM_FRACTION) {
            Log.d(TAG, "Skip margin crop: trim too small (removedH=$removedH removedW=$removedW)")
            return bitmap
        }
        if (removedH > MAX_TRIM_FRACTION || removedW > MAX_TRIM_FRACTION) {
            Log.w(TAG, "Skip margin crop: would remove too much (removedH=$removedH removedW=$removedW)")
            return bitmap
        }

        return try {
            val out = Bitmap.createBitmap(bitmap, left, top, cw, ch)
            Log.i(
                TAG,
                "Margin crop: ${bitmap.width}x${bitmap.height} → ${cw}x${ch} " +
                    "(removed L:$left T:$top R:${bitmap.width - right} B:${bitmap.height - bottom})"
            )
            out
        } catch (e: Exception) {
            Log.w(TAG, "Margin crop failed: ${e.message}")
            bitmap
        }
    }

    /**
     * Returns bounds in **original bitmap** pixel coordinates, or null if estimation failed.
     */
    private fun estimateBounds(bitmap: Bitmap): Bounds? {
        val w0 = bitmap.width
        val h0 = bitmap.height
        if (w0 < 64 || h0 < 64) return null

        val scale = min(ANALYSIS_MAX_SIDE.toFloat() / max(w0, h0), 1f)
        val w = max(32, (w0 * scale).roundToInt())
        val h = max(32, (h0 * scale).roundToInt())
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val gray = IntArray(w * h)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()
        }

        val gx = FloatArray(w * h)
        val gy = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val g = gray[i]
                gx[i] = (g - gray[i - 1]).toFloat()
                gy[i] = (g - gray[i - w]).toFloat()
            }
        }

        val mag = FloatArray(w * h)
        for (i in mag.indices) {
            mag[i] = sqrt(gx[i] * gx[i] + gy[i] * gy[i])
        }

        val rowE = FloatArray(h)
        for (y in 1 until h - 1) {
            var s = 0f
            var n = 0
            for (x in 1 until w - 1) {
                s += mag[y * w + x]
                n++
            }
            rowE[y] = if (n > 0) s / n else 0f
        }
        rowE[0] = rowE[1]
        rowE[h - 1] = rowE[h - 2]

        val colE = FloatArray(w)
        for (x in 1 until w - 1) {
            var s = 0f
            var n = 0
            for (y in 1 until h - 1) {
                s += mag[y * w + x]
                n++
            }
            colE[x] = if (n > 0) s / n else 0f
        }
        colE[0] = colE[1]
        colE[w - 1] = colE[w - 2]

        smooth1dInPlace(rowE, SMOOTH_RADIUS)
        smooth1dInPlace(colE, SMOOTH_RADIUS)

        val rowPeak = rowE.maxOrNull() ?: return null
        val colPeak = colE.maxOrNull() ?: return null
        if (rowPeak <= 1e-6f || colPeak <= 1e-6f) return null

        val rowMed = percentile(rowE, 0.5f)
        val colMed = percentile(colE, 0.5f)
        // Between peak and median: margins sit near median energy; document band is higher.
        val rowTh = max(rowPeak * FRACTION_OF_PEAK, rowMed * 1.85f)
        val colTh = max(colPeak * FRACTION_OF_PEAK, colMed * 1.85f)

        val run = MIN_CONTENT_RUN.coerceAtMost(min(h, w) / 4).coerceAtLeast(4)

        var top = findTopByWindow(rowE, rowTh, run)
        var bottom = findBottomByWindow(rowE, rowTh, run)
        var left = findTopByWindow(colE, colTh, run)
        var right = findBottomByWindow(colE, colTh, run)

        if (bottom <= top || right <= left) return null

        // Map back to original resolution
        val inv = 1f / scale
        val oLeft = (left * inv).roundToInt().coerceIn(0, w0 - 1)
        val oTop = (top * inv).roundToInt().coerceIn(0, h0 - 1)
        val oRight = (right * inv).roundToInt().coerceIn(oLeft + 1, w0)
        val oBottom = (bottom * inv).roundToInt().coerceIn(oTop + 1, h0)

        return Bounds(oLeft, oTop, oRight, oBottom)
    }

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** First index where a window of [run] values averages to at least [th] (top / left). */
    private fun findTopByWindow(a: FloatArray, th: Float, run: Int): Int {
        val n = a.size
        if (n < run) return 0
        for (i in 0..n - run) {
            var s = 0f
            for (k in 0 until run) s += a[i + k]
            if (s / run >= th) return i
        }
        return 0
    }

    /** Last index (inclusive) of the bottommost window whose average is at least [th]. */
    private fun findBottomByWindow(a: FloatArray, th: Float, run: Int): Int {
        val n = a.size
        if (n < run) return n - 1
        for (i in n - run downTo 0) {
            var s = 0f
            for (k in 0 until run) s += a[i + k]
            if (s / run >= th) return i + run - 1
        }
        return n - 1
    }

    private fun smooth1dInPlace(a: FloatArray, r: Int) {
        val n = a.size
        val tmp = FloatArray(n)
        for (i in 0 until n) {
            var s = 0f
            var c = 0
            for (d in -r..r) {
                val j = i + d
                if (j in 0 until n) {
                    s += a[j]
                    c++
                }
            }
            tmp[i] = if (c > 0) s / c else a[i]
        }
        for (i in 0 until n) a[i] = tmp[i]
    }

    private fun percentile(a: FloatArray, q: Float): Float {
        val sorted = a.sorted()
        val idx = ((sorted.size - 1) * q).roundToInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }
}
