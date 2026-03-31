package com.example.flutter_ocr_poc.ocr.docprep

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Corrects geometric distortion in document images using UVDoc ONNX model.
 *
 * Input:  [1, 3, H, W] float32 — RGB normalized to [0, 1]
 * Output: [1, 3, H, W] float32 — corrected image in [0, 1]
 *
 * The model accepts dynamic H/W but internally works best with dimensions
 * divisible by 32. We resize to a standard size, run inference, then resize back.
 */
class DocUnwarpRunner(modelPath: String) {

    companion object {
        private const val TAG = "DocUnwarpRunner"
        private const val TARGET_H = 488
        private const val TARGET_W = 712
    }

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelPath, OrtSession.SessionOptions())

    /**
     * Unwarp the document image. Returns the corrected bitmap.
     */
    fun unwarp(bitmap: Bitmap): Bitmap {
        val origW = bitmap.width
        val origH = bitmap.height

        val resized = Bitmap.createScaledBitmap(bitmap, TARGET_W, TARGET_H, true)
        val inputData = bitmapToChw(resized)
        if (resized != bitmap) resized.recycle()

        val inputName = session.inputNames.iterator().next()
        val inputShape = longArrayOf(1, 3, TARGET_H.toLong(), TARGET_W.toLong())
        val buffer = ByteBuffer.allocateDirect(inputData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(inputData)
        buffer.rewind()

        val tensor = OnnxTensor.createTensor(env, buffer, inputShape)
        try {
            val result = session.run(mapOf(inputName to tensor))
            val output = result.get(0) as OnnxTensor
            try {
                val outBuf = output.floatBuffer
                    ?: throw IllegalStateException("UVDoc output is not float")
                val outShape = output.info.shape
                val outH = outShape[2].toInt()
                val outW = outShape[3].toInt()

                val outData = FloatArray(outBuf.remaining())
                outBuf.rewind()
                outBuf.get(outData)

                val corrected = chwToBitmap(outData, outH, outW)
                Log.i(TAG, "Unwarped: ${origW}x${origH} → ${outW}x${outH}")

                // Resize back to original dimensions
                val final_ = if (corrected.width != origW || corrected.height != origH) {
                    val scaled = Bitmap.createScaledBitmap(corrected, origW, origH, true)
                    corrected.recycle()
                    scaled
                } else {
                    corrected
                }
                return final_
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }
    }

    private fun bitmapToChw(bitmap: Bitmap): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val data = FloatArray(3 * h * w)
        for (i in 0 until h * w) {
            val p = pixels[i]
            data[i] = ((p shr 16) and 0xFF) / 255.0f                 // R
            data[h * w + i] = ((p shr 8) and 0xFF) / 255.0f          // G
            data[2 * h * w + i] = (p and 0xFF) / 255.0f              // B
        }
        return data
    }

    private fun chwToBitmap(data: FloatArray, h: Int, w: Int): Bitmap {
        val pixels = IntArray(w * h)
        val planeSize = h * w
        for (i in 0 until planeSize) {
            val r = (data[i].coerceIn(0f, 1f) * 255).roundToInt()
            val g = (data[planeSize + i].coerceIn(0f, 1f) * 255).roundToInt()
            val b = (data[2 * planeSize + i].coerceIn(0f, 1f) * 255).roundToInt()
            pixels[i] = Color.rgb(r, g, b)
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    fun close() {
        session.close()
    }
}
