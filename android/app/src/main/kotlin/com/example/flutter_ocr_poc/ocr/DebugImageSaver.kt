package com.example.flutter_ocr_poc.ocr

import android.content.Context
import android.graphics.*
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves intermediate pipeline bitmaps to disk for debugging OCR recognition issues.
 *
 * Directory structure per session:
 * ```
 * <cacheDir>/ocr_debug/<timestamp>/
 *   detection_overlay.png
 *   region_000/
 *     01_cropped.png
 *     02_trimmed.png
 *     03_preprocessed.png
 *     04_clahe.png
 *   region_001/
 *     ...
 * ```
 *
 * All methods are no-ops when [enabled] is false.
 */
class DebugImageSaver(
    private val context: Context,
    var enabled: Boolean = false
) {
    companion object {
        private const val TAG = "DebugImageSaver"
        private const val DEBUG_DIR = "ocr_debug"
        private const val MAX_SESSIONS = 5
    }

    private var sessionDir: File? = null

    /**
     * Start a new debug session. Creates a timestamped directory.
     * @return the absolute path of the session directory, or null if disabled.
     */
    fun startSession(): String? {
        if (!enabled) return null

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val baseDir = File(context.cacheDir, DEBUG_DIR)
        val dir = File(baseDir, timestamp)
        dir.mkdirs()
        sessionDir = dir

        cleanupOldSessions(MAX_SESSIONS)

        Log.i(TAG, "Debug session started: ${dir.absolutePath}")
        return dir.absolutePath
    }

    /**
     * Save the full image with all detected bounding boxes drawn as a red overlay.
     */
    fun saveDetectionOverlay(bitmap: Bitmap, boxes: List<List<FloatArray>>) {
        if (!enabled) return
        val dir = sessionDir ?: return

        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(copy)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.RED
            textSize = 28f
            isAntiAlias = true
            isFakeBoldText = true
        }

        for ((index, box) in boxes.withIndex()) {
            if (box.size < 4) continue
            val path = Path()
            path.moveTo(box[0][0], box[0][1])
            for (i in 1 until box.size) {
                path.lineTo(box[i][0], box[i][1])
            }
            path.close()
            canvas.drawPath(path, paint)

            // Draw region number
            canvas.drawText(
                index.toString(),
                box[0][0] + 4f,
                box[0][1] - 4f,
                textPaint
            )
        }

        saveBitmap(copy, File(dir, "detection_overlay.png"))
        copy.recycle()
    }

    /**
     * Save a bitmap for a specific pipeline stage within a region.
     */
    fun saveRegionStage(bitmap: Bitmap, regionIndex: Int, stageName: String) {
        if (!enabled) return
        val dir = sessionDir ?: return

        val regionDir = File(dir, "region_%03d".format(regionIndex))
        regionDir.mkdirs()
        saveBitmap(bitmap, File(regionDir, "$stageName.png"))
    }

    /**
     * Delete old debug sessions, keeping only the most recent [keepCount].
     */
    private fun cleanupOldSessions(keepCount: Int) {
        val baseDir = File(context.cacheDir, DEBUG_DIR)
        if (!baseDir.exists()) return

        val sessions = baseDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?: return

        if (sessions.size > keepCount) {
            for (old in sessions.drop(keepCount)) {
                old.deleteRecursively()
                Log.d(TAG, "Cleaned up old debug session: ${old.name}")
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d(TAG, "Saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save debug image ${file.name}: ${e.message}")
        }
    }
}
