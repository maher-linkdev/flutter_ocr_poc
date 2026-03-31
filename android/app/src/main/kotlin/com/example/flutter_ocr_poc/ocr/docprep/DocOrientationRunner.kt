package com.example.flutter_ocr_poc.ocr.docprep

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Classifies document orientation (0°, 90°, 180°, 270°) using PP-LCNet_x1_0_doc_ori ONNX model.
 *
 * Input:  [1, 3, 224, 224] float32 — RGB, normalized with ImageNet mean/std
 * Output: [1, 4] float32 — softmax scores for [0°, 90°, 180°, 270°]
 */
class DocOrientationRunner(modelPath: String) {

    companion object {
        private const val TAG = "DocOrientationRunner"
        private const val INPUT_SIZE = 224
        private val ANGLES = intArrayOf(0, 90, 180, 270)
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelPath, OrtSession.SessionOptions())

    /**
     * Classify and auto-rotate the image. Returns the corrected bitmap and the detected angle.
     */
    fun classifyAndRotate(bitmap: Bitmap): Pair<Bitmap, Int> {
        val angle = classify(bitmap)
        if (angle == 0) return Pair(bitmap, 0)

        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        Log.i(TAG, "Rotated image by ${angle}° (detected orientation)")
        return Pair(rotated, angle)
    }

    /**
     * Returns the detected rotation angle (0, 90, 180, or 270).
     */
    private fun classify(bitmap: Bitmap): Int {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputData = bitmapToChw(resized)
        if (resized != bitmap) resized.recycle()

        val inputName = session.inputNames.iterator().next()
        val inputShape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
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
                val scores = output.floatBuffer
                    ?: throw IllegalStateException("Orientation output is not float")
                val scoreArray = FloatArray(4)
                scores.rewind()
                scores.get(scoreArray)

                var maxIdx = 0
                var maxVal = scoreArray[0]
                for (i in 1 until 4) {
                    if (scoreArray[i] > maxVal) {
                        maxVal = scoreArray[i]
                        maxIdx = i
                    }
                }

                val angle = ANGLES[maxIdx]
                Log.i(TAG, "Orientation: ${angle}° (scores: ${scoreArray.joinToString { "%.3f".format(it) }})")
                return angle
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
            val r = ((p shr 16) and 0xFF) / 255.0f
            val g = ((p shr 8) and 0xFF) / 255.0f
            val b = (p and 0xFF) / 255.0f
            data[i] = (r - MEAN[0]) / STD[0]
            data[h * w + i] = (g - MEAN[1]) / STD[1]
            data[2 * h * w + i] = (b - MEAN[2]) / STD[2]
        }
        return data
    }

    fun close() {
        session.close()
    }
}
