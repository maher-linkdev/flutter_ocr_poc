package com.example.flutter_ocr_poc.ocr.preprocessing

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * ESPCN 2× super-resolution via ONNX Runtime — upscales small text crops.
 *
 * Uses YCbCr decomposition: SR on the Y (luminance) channel only,
 * bicubic upscale for Cb/Cr, then recombine to RGB.
 * Pattern follows RecOnnxRunner.kt.
 */
class SuperResOnnxRunner(modelPath: String) {

    companion object {
        private const val TAG = "SuperResOnnxRunner"
        private const val SCALE = 2
    }

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelPath, OrtSession.SessionOptions())

    /**
     * Upscale a bitmap by 2×. Returns a new bitmap; input is not modified.
     */
    fun process(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Convert RGB → YCbCr
        val y = FloatArray(w * h)
        val cb = FloatArray(w * h)
        val cr = FloatArray(w * h)

        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()

            y[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            cb[i] = (-0.169f * r - 0.331f * g + 0.500f * b + 128f) / 255f
            cr[i] = (0.500f * r - 0.419f * g - 0.081f * b + 128f) / 255f
        }

        // Run ESPCN on Y channel
        val srY = runSuperRes(y, h, w)
        val outH = h * SCALE
        val outW = w * SCALE

        // Bicubic upscale Cb and Cr channels
        val srCb = bilinearUpscale(cb, w, h, outW, outH)
        val srCr = bilinearUpscale(cr, w, h, outW, outH)

        // Convert YCbCr → RGB and build output bitmap
        val outPixels = IntArray(outW * outH)
        for (i in 0 until outW * outH) {
            val yVal = srY[i] * 255f
            val cbVal = srCb[i] * 255f - 128f
            val crVal = srCr[i] * 255f - 128f

            val r = (yVal + 1.402f * crVal).roundToInt().coerceIn(0, 255)
            val g = (yVal - 0.344f * cbVal - 0.714f * crVal).roundToInt().coerceIn(0, 255)
            val b = (yVal + 1.772f * cbVal).roundToInt().coerceIn(0, 255)

            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        output.setPixels(outPixels, 0, outW, 0, 0, outW, outH)
        return output
    }

    /**
     * Run ESPCN inference on a single-channel (Y) float array normalized to [0,1].
     * Returns the super-resolved Y channel as a float array.
     */
    private fun runSuperRes(input: FloatArray, h: Int, w: Int): FloatArray {
        val inputName = session.inputNames.iterator().next()
        val inputShape = longArrayOf(1, 1, h.toLong(), w.toLong())

        val buffer = ByteBuffer.allocateDirect(input.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(input)
        buffer.rewind()

        val tensor = OnnxTensor.createTensor(env, buffer, inputShape)
        try {
            val result = session.run(mapOf(inputName to tensor))
            val output = result.get(0) as OnnxTensor
            try {
                val outBuf = output.floatBuffer
                    ?: throw IllegalStateException("SR ONNX output is not float type")
                val outputData = FloatArray(outBuf.remaining())
                outBuf.rewind()
                outBuf.get(outputData)
                return outputData
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }
    }

    /**
     * Simple bilinear upscale for Cb/Cr channels.
     */
    private fun bilinearUpscale(
        src: FloatArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): FloatArray {
        val dst = FloatArray(dstW * dstH)
        val xRatio = srcW.toFloat() / dstW
        val yRatio = srcH.toFloat() / dstH

        for (dy in 0 until dstH) {
            val sy = dy * yRatio
            val y0 = sy.toInt().coerceIn(0, srcH - 1)
            val y1 = (y0 + 1).coerceAtMost(srcH - 1)
            val fy = sy - y0

            for (dx in 0 until dstW) {
                val sx = dx * xRatio
                val x0 = sx.toInt().coerceIn(0, srcW - 1)
                val x1 = (x0 + 1).coerceAtMost(srcW - 1)
                val fx = sx - x0

                val tl = src[y0 * srcW + x0]
                val tr = src[y0 * srcW + x1]
                val bl = src[y1 * srcW + x0]
                val br = src[y1 * srcW + x1]

                dst[dy * dstW + dx] = tl * (1 - fx) * (1 - fy) +
                        tr * fx * (1 - fy) +
                        bl * (1 - fx) * fy +
                        br * fx * fy
            }
        }
        return dst
    }

    fun close() {
        session.close()
        Log.d(TAG, "SuperRes ONNX session closed")
    }
}
