package com.example.flutter_ocr_poc

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.example.flutter_ocr_poc.ocr.OcrMethodHandler

/**
 * Main activity that registers the PaddleOCR MethodChannel.
 *
 * The MethodChannel bridges Flutter (Dart) with native Android
 * PaddleOCR inference via Paddle Lite.
 */
class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "com.example.flutter_ocr_poc/ocr"
    }

    private var ocrMethodHandler: OcrMethodHandler? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        ocrMethodHandler = OcrMethodHandler(this)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler(ocrMethodHandler)
    }

    override fun onDestroy() {
        ocrMethodHandler?.dispose()
        super.onDestroy()
    }
}
