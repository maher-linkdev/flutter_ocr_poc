package com.example.flutter_ocr_poc.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Runs PP-OCRv5 recognition using ONNX Runtime (new PIR→ONNX path).
 * Use when the rec model is in ONNX format (e.g. Arabic from PIR conversion).
 */
class RecOnnxRunner(private val modelPath: String) {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelPath, OrtSession.SessionOptions())

    /**
     * Run recognition inference. Returns (outputData, outputShape) for CTC decode.
     */
    fun run(inputData: FloatArray, imgH: Int, imgW: Int): Pair<FloatArray, LongArray> {
        val inputName = session.inputNames.iterator().next()
        val inputShape = longArrayOf(1, 3, imgH.toLong(), imgW.toLong())

        // Use direct FloatBuffer (recommended by ONNX Runtime)
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
                    ?: throw IllegalStateException("Rec ONNX output is not float type")
                val outputData = FloatArray(outBuf.remaining())
                outBuf.rewind()
                outBuf.get(outputData)
                val outputShape = output.info.shape
                return Pair(outputData, outputShape)
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }
    }

    fun close() {
        session.close()
    }
}
