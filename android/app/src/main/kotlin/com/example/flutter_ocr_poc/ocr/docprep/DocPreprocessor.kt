package com.example.flutter_ocr_poc.ocr.docprep

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Full-image document preprocessor that runs BEFORE the OCR detection pipeline.
 *
 * Pipeline:  Original image → Orientation → (optional) margin crop → UVDoc unwarp → Save result
 *
 * Both stages are optional and controlled by flags passed at initialization.
 */
class DocPreprocessor(private val context: Context) {

    companion object {
        private const val TAG = "DocPreprocessor"
    }

    private var orientationRunner: DocOrientationRunner? = null
    private var unwarpRunner: DocUnwarpRunner? = null

    fun initialize(
        orientationModelPath: String?,
        unwarpModelPath: String?
    ) {
        if (orientationModelPath != null) {
            orientationRunner = DocOrientationRunner(orientationModelPath)
            Log.i(TAG, "Doc orientation model loaded: $orientationModelPath")
        }
        if (unwarpModelPath != null) {
            unwarpRunner = DocUnwarpRunner(unwarpModelPath)
            Log.i(TAG, "Doc unwarp model loaded: $unwarpModelPath")
        }
    }

    val isEnabled: Boolean
        get() = orientationRunner != null || unwarpRunner != null

    /**
     * Run the full preprocessing pipeline on the image.
     *
     * @return DocPrepResult with the preprocessed bitmap and metadata
     */
    fun process(bitmap: Bitmap): DocPrepResult {
        val startTime = System.currentTimeMillis()
        var current = bitmap
        var rotationAngle = 0
        var didUnwarp = false

        // Step 1: Orientation correction
        orientationRunner?.let { runner ->
            val (rotated, angle) = runner.classifyAndRotate(current)
            rotationAngle = angle
            if (rotated !== current) {
                if (current !== bitmap) current.recycle()
                current = rotated
            }
        }

        // Step 1b: Tight crop around document content (gradient energy) before UVDoc.
        // Large top/bottom (or left/right) table margins stay in-frame after unwarp if we skip this,
        // because UVDoc scales the entire photo to a fixed aspect — shrinking the card in the tensor.
        if (unwarpRunner != null) {
            val cropped = DocumentMarginCropper.cropIfNeeded(current)
            if (cropped !== current) {
                if (current !== bitmap) current.recycle()
                current = cropped
            }
        }

        // Step 2: Document unwarping
        unwarpRunner?.let { runner ->
            val unwarped = runner.unwarp(current)
            didUnwarp = true
            if (unwarped !== current) {
                if (current !== bitmap) current.recycle()
                current = unwarped
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "Doc preprocessing: ${elapsed}ms (rotation=${rotationAngle}°, unwarp=$didUnwarp)")

        return DocPrepResult(
            bitmap = current,
            rotationAngle = rotationAngle,
            didUnwarp = didUnwarp,
            processingTimeMs = elapsed
        )
    }

    /**
     * Save the preprocessed image and return its absolute path.
     *
     * When [sessionDir] is set (debug session), writes `00_doc_preprocessed.png` next to other
     * pipeline artifacts. Otherwise writes to app cache so the Flutter UI can still show the
     * doc-prep result when debug image saving is disabled.
     */
    fun savePreprocessedImage(bitmap: Bitmap, sessionDir: String?): String? {
        val file = if (sessionDir != null) {
            File(sessionDir, "00_doc_preprocessed.png")
        } else {
            val dir = File(context.cacheDir, "ocr_doc_prep")
            dir.mkdirs()
            File(dir, "last_doc_preprocessed.png")
        }
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d(TAG, "Saved preprocessed: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save preprocessed image: ${e.message}")
            null
        }
    }

    fun close() {
        orientationRunner?.close()
        orientationRunner = null
        unwarpRunner?.close()
        unwarpRunner = null
    }
}

data class DocPrepResult(
    val bitmap: Bitmap,
    val rotationAngle: Int,
    val didUnwarp: Boolean,
    val processingTimeMs: Long
)
