package com.example.flutter_ocr_poc.ocr

import android.content.Context
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*

/**
 * Handles MethodChannel calls from Flutter for PaddleOCR operations.
 *
 * Delegates actual OCR work to [PaddleOcrEngine] running on a
 * background coroutine to avoid blocking the main thread.
 */
class OcrMethodHandler(
    private val context: Context
) : MethodChannel.MethodCallHandler {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var ocrEngine: PaddleOcrEngine? = null

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "initOcr" -> handleInitOcr(call, result)
            "recognizeText" -> handleRecognizeText(call, result)
            "dispose" -> handleDispose(result)
            else -> result.notImplemented()
        }
    }

    /**
     * Initialize the PaddleOCR engine with model files from assets.
     *
     * Expected arguments:
     * - detModelFileName: String — detection model .nb file
     * - recModelFileName: String — recognition model .nb file
     * - clsModelFileName: String — classification model .nb file
     * - labelFileName: String — character dictionary file
     * - threadCount: Int — number of CPU threads
     */
    private fun handleInitOcr(call: MethodCall, result: MethodChannel.Result) {
        scope.launch(Dispatchers.IO) {
            try {
                val detModel = call.argument<String>("detModelFileName")
                    ?: throw IllegalArgumentException("detModelFileName required")
                val recModel = call.argument<String>("recModelFileName")
                    ?: throw IllegalArgumentException("recModelFileName required")
                val clsModel = call.argument<String>("clsModelFileName")
                    ?: throw IllegalArgumentException("clsModelFileName required")
                val labelFile = call.argument<String>("labelFileName")
                    ?: throw IllegalArgumentException("labelFileName required")
                val threadCount = call.argument<Int>("threadCount") ?: 4
                val enableContrastEnhance = call.argument<Boolean>("enableContrastEnhance") ?: false
                val recOnnxFileName = call.argument<String>("recOnnxFileName")
                val enablePreprocessing = call.argument<Boolean>("enablePreprocessing") ?: false
                val superResModelFileName = call.argument<String>("superResModelFileName")

                val engine = PaddleOcrEngine(context)
                engine.initialize(
                    detModelFileName = detModel,
                    recModelFileName = recModel,
                    clsModelFileName = clsModel,
                    labelFileName = labelFile,
                    threadCount = threadCount,
                    enableContrastEnhance = enableContrastEnhance,
                    recOnnxFileName = recOnnxFileName,
                    enablePreprocessing = enablePreprocessing,
                    superResModelFileName = superResModelFileName
                )
                ocrEngine = engine

                withContext(Dispatchers.Main) {
                    result.success(true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    result.error("OCR_INIT_ERROR", e.message, null)
                }
            }
        }
    }

    /**
     * Run OCR text recognition on the provided image.
     *
     * Expected arguments:
     * - imagePath: String — absolute path to the image file
     *
     * Returns a Map with:
     * - textBlocks: List<Map> — each with text, boundingBox, confidence
     * - processingTimeMs: Int — total inference time
     * - imagePath: String — the input image path
     */
    private fun handleRecognizeText(call: MethodCall, result: MethodChannel.Result) {
        scope.launch(Dispatchers.IO) {
            try {
                val engine = ocrEngine
                    ?: throw IllegalStateException("OCR engine not initialized. Call initOcr first.")

                val imagePath = call.argument<String>("imagePath")
                    ?: throw IllegalArgumentException("imagePath required")

                val ocrResult = engine.recognizeText(imagePath)

                withContext(Dispatchers.Main) {
                    result.success(ocrResult)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    result.error("OCR_RECOGNITION_ERROR", e.message, null)
                }
            }
        }
    }

    /**
     * Release all native OCR resources.
     */
    private fun handleDispose(result: MethodChannel.Result) {
        try {
            ocrEngine?.release()
            ocrEngine = null
            result.success(true)
        } catch (e: Exception) {
            result.error("OCR_DISPOSE_ERROR", e.message, null)
        }
    }

    /**
     * Clean up when the handler is destroyed.
     */
    fun dispose() {
        ocrEngine?.release()
        ocrEngine = null
        scope.cancel()
    }
}
