package com.example.flutter_ocr_poc.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Native PaddleOCR engine using Paddle Lite for on-device inference.
 *
 * Pipeline: Image → Detection → Crop → Classification → Recognition
 */
class PaddleOcrEngine(private val context: Context) {

    companion object {
        private const val TAG = "PaddleOcrEngine"
        private const val MODELS_DIR = "models"
        private const val LABELS_DIR = "labels"

        // Detection thresholds (lowered for better ID card number detection)
        private const val DET_DB_THRESH = 0.2f
        private const val DET_DB_BOX_THRESH = 0.3f
        private const val DET_DB_UNCLIP_RATIO = 1.8f
        private const val MAX_SIDE_LEN = 1280

        // Arabic Unicode range for RTL detection
        private const val ARABIC_RANGE_START = '\u0600'
        private const val ARABIC_RANGE_END = '\u06FF'
        private const val ARABIC_EXT_A_START = '\u0750'
        private const val ARABIC_EXT_A_END = '\u077F'
        private const val ARABIC_PRESENTATION_A_START = '\uFB50'
        private const val ARABIC_PRESENTATION_A_END = '\uFDFF'
        private const val ARABIC_PRESENTATION_B_START = '\uFE70'
        private const val ARABIC_PRESENTATION_B_END = '\uFEFF'

        // Recognition
        private const val REC_IMAGE_HEIGHT = 48

        // ImageNet normalization
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private var detPredictor: PaddlePredictor? = null
    private var recPredictor: PaddlePredictor? = null
    private var clsPredictor: PaddlePredictor? = null

    private var detModelPath: String? = null
    private var recModelPath: String? = null
    private var clsModelPath: String? = null
    private var labelPath: String? = null
    private var threadCount: Int = 4
    private var isInitialized = false

    private var labelList: List<String> = emptyList()

    /**
     * Initialize the OCR engine by copying models and loading predictors.
     */
    fun initialize(
        detModelFileName: String,
        recModelFileName: String,
        clsModelFileName: String,
        labelFileName: String,
        threadCount: Int = 4
    ) {
        this.threadCount = threadCount

        // Copy assets to internal storage
        detModelPath = copyAssetToInternal("$MODELS_DIR/$detModelFileName")
        recModelPath = copyAssetToInternal("$MODELS_DIR/$recModelFileName")
        clsModelPath = copyAssetToInternal("$MODELS_DIR/$clsModelFileName")
        labelPath = copyAssetToInternal("$LABELS_DIR/$labelFileName")

        // Load character dictionary
        labelList = loadLabelList(labelPath!!)

        // Load Paddle Lite predictors
        detPredictor = loadModel(detModelPath!!)
        recPredictor = loadModel(recModelPath!!)
        clsPredictor = loadModel(clsModelPath!!)

        isInitialized = true
        Log.i(TAG, "OCR Engine initialized successfully")
        Log.i(TAG, "  Detection model: $detModelPath")
        Log.i(TAG, "  Recognition model: $recModelPath")
        Log.i(TAG, "  Classification model: $clsModelPath")
        Log.i(TAG, "  Label dictionary: ${labelList.size} characters")
    }

    /**
     * Run the full OCR pipeline on an image.
     */
    fun recognizeText(imagePath: String): Map<String, Any> {
        if (!isInitialized) {
            throw IllegalStateException("OCR engine not initialized")
        }

        val startTime = System.currentTimeMillis()

        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw IllegalArgumentException("Cannot decode image: $imagePath")

        val imgWidth = bitmap.width
        val imgHeight = bitmap.height
        Log.d(TAG, "Input image: ${imgWidth}x${imgHeight}")

        // Step 1: Detection — find text regions
        val boxes = runDetection(bitmap)
        Log.d(TAG, "Detected ${boxes.size} text regions")

        // Step 2+3: For each box, crop → classify → recognize
        val textBlocks = mutableListOf<Map<String, Any>>()
        for (box in boxes) {
            try {
                // Crop text region from original image
                val cropped = cropTextRegion(bitmap, box)
                if (cropped == null || cropped.width < 2 || cropped.height < 2) continue

                // Classify text orientation and rotate if needed
                val oriented = classifyAndRotate(cropped)

                // Recognize text
                val (text, confidence) = runRecognition(oriented)

                if (text.isNotBlank() && confidence > 0.1f) {
                    textBlocks.add(
                        mapOf(
                            "text" to text,
                            "boundingBox" to mapOf(
                                "points" to box.map { listOf(it[0].toDouble(), it[1].toDouble()) }
                            ),
                            "confidence" to confidence.toDouble()
                        )
                    )
                }

                oriented.recycle()
            } catch (e: Exception) {
                Log.w(TAG, "Error processing text region: ${e.message}")
            }
        }

        val processingTimeMs = System.currentTimeMillis() - startTime
        Log.i(TAG, "OCR completed: ${textBlocks.size} text blocks in ${processingTimeMs}ms")

        bitmap.recycle()

        return mapOf(
            "textBlocks" to textBlocks,
            "processingTimeMs" to processingTimeMs.toInt(),
            "imagePath" to imagePath
        )
    }

    /**
     * Release all native resources.
     */
    fun release() {
        detPredictor = null
        recPredictor = null
        clsPredictor = null
        isInitialized = false
        Log.i(TAG, "OCR Engine released")
    }

    // ─── Model Loading ────────────────────────────────────────────

    private fun loadModel(modelPath: String): PaddlePredictor {
        val config = MobileConfig()
        config.setModelFromFile(modelPath)
        config.setThreads(threadCount)
        config.setPowerMode(PowerMode.LITE_POWER_HIGH)
        return PaddlePredictor.createPaddlePredictor(config)
    }

    // ─── Detection Pipeline ───────────────────────────────────────

    /**
     * Run text detection on the full image.
     * Returns list of quadrilateral boxes (4 points each).
     */
    private fun runDetection(bitmap: Bitmap): List<List<FloatArray>> {
        val predictor = detPredictor ?: throw IllegalStateException("Detection model not loaded")

        // Resize with padding to multiple of 32
        val (resized, ratioH, ratioW) = resizeForDetection(bitmap)
        val h = resized.height
        val w = resized.width

        // Convert to CHW float array with normalization
        val inputData = bitmapToCHW(resized, MEAN, STD)

        // Run inference
        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(1, 3, h.toLong(), w.toLong()))
        inputTensor.setData(inputData)
        predictor.run()

        // Get output probability map
        val outputTensor = predictor.getOutput(0)
        val outputShape = outputTensor.shape()
        val outputData = outputTensor.getFloatData()
        val outH = outputShape[2].toInt()
        val outW = outputShape[3].toInt()

        if (resized != bitmap) resized.recycle()

        // Post-process: threshold → connected components → boxes
        return dbPostProcess(outputData, outH, outW, ratioH, ratioW, bitmap.height, bitmap.width)
    }

    /**
     * Resize image for detection model: max side ≤ MAX_SIDE_LEN, dimensions multiple of 32.
     */
    private fun resizeForDetection(bitmap: Bitmap): Triple<Bitmap, Float, Float> {
        var w = bitmap.width
        var h = bitmap.height

        var ratio = 1.0f
        if (max(h, w) > MAX_SIDE_LEN) {
            ratio = if (h > w) {
                MAX_SIDE_LEN.toFloat() / h
            } else {
                MAX_SIDE_LEN.toFloat() / w
            }
        }

        var resizeH = (h * ratio).roundToInt()
        var resizeW = (w * ratio).roundToInt()

        // Round to multiple of 32
        resizeH = if (resizeH % 32 == 0) resizeH else (resizeH / 32 + 1) * 32
        resizeW = if (resizeW % 32 == 0) resizeW else (resizeW / 32 + 1) * 32

        val ratioH = h.toFloat() / resizeH
        val ratioW = w.toFloat() / resizeW

        val resized = Bitmap.createScaledBitmap(bitmap, resizeW, resizeH, true)
        return Triple(resized, ratioH, ratioW)
    }

    /**
     * DB post-processing: threshold → binary map → connected components → bounding boxes.
     */
    private fun dbPostProcess(
        outputData: FloatArray,
        outH: Int,
        outW: Int,
        ratioH: Float,
        ratioW: Float,
        srcHeight: Int,
        srcWidth: Int
    ): List<List<FloatArray>> {
        // Create binary map from probability map
        val binaryMap = IntArray(outH * outW)
        for (i in outputData.indices) {
            if (i < outH * outW) {
                binaryMap[i] = if (outputData[i] > DET_DB_THRESH) 1 else 0
            }
        }

        // Connected component labeling
        val labels = connectedComponents(binaryMap, outW, outH)
        val maxLabel = labels.max()

        val boxes = mutableListOf<List<FloatArray>>()

        for (label in 1..maxLabel) {
            // Find all pixels for this component
            val points = mutableListOf<IntArray>()
            var scoreSum = 0.0f
            for (y in 0 until outH) {
                for (x in 0 until outW) {
                    val idx = y * outW + x
                    if (labels[idx] == label) {
                        points.add(intArrayOf(x, y))
                        if (idx < outputData.size) {
                            scoreSum += outputData[idx]
                        }
                    }
                }
            }

            if (points.size < 4) continue

            val avgScore = scoreSum / points.size
            if (avgScore < DET_DB_BOX_THRESH) continue

            // Get min bounding rect
            val minX = points.minOf { it[0] }
            val maxX = points.maxOf { it[0] }
            val minY = points.minOf { it[1] }
            val maxY = points.maxOf { it[1] }

            val boxW = (maxX - minX).toFloat()
            val boxH = (maxY - minY).toFloat()

            if (boxW < 2 || boxH < 2) continue

            // Unclip: expand box
            val area = boxW * boxH
            val perimeter = 2 * (boxW + boxH)
            val distance = area * DET_DB_UNCLIP_RATIO / perimeter

            val expandMinX = max(0f, minX - distance) * ratioW
            val expandMinY = max(0f, minY - distance) * ratioH
            val expandMaxX = min(srcWidth.toFloat(), (maxX + distance) * ratioW)
            val expandMaxY = min(srcHeight.toFloat(), (maxY + distance) * ratioH)

            // Quadrilateral box (4 corners: TL, TR, BR, BL)
            val box = listOf(
                floatArrayOf(expandMinX, expandMinY),
                floatArrayOf(expandMaxX, expandMinY),
                floatArrayOf(expandMaxX, expandMaxY),
                floatArrayOf(expandMinX, expandMaxY)
            )
            boxes.add(box)
        }

        // Sort boxes top-to-bottom, left-to-right
        return boxes.sortedWith(compareBy({ it[0][1] }, { it[0][0] }))
    }

    /**
     * Two-pass connected component labeling.
     */
    private fun connectedComponents(binary: IntArray, width: Int, height: Int): IntArray {
        val labels = IntArray(width * height)
        val parent = mutableMapOf<Int, Int>()
        var nextLabel = 1

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) {
                root = parent[root] ?: root
            }
            // Path compression
            var curr = x
            while (curr != root) {
                val next = parent[curr] ?: root
                parent[curr] = root
                curr = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) {
                parent[ra] = rb
            }
        }

        // First pass
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (binary[idx] == 0) continue

                val neighbors = mutableListOf<Int>()
                if (x > 0 && labels[idx - 1] > 0) neighbors.add(labels[idx - 1])
                if (y > 0 && labels[idx - width] > 0) neighbors.add(labels[idx - width])

                if (neighbors.isEmpty()) {
                    labels[idx] = nextLabel
                    parent[nextLabel] = nextLabel
                    nextLabel++
                } else {
                    val minLabel = neighbors.min()
                    labels[idx] = minLabel
                    for (n in neighbors) {
                        union(n, minLabel)
                    }
                }
            }
        }

        // Second pass: resolve labels
        for (i in labels.indices) {
            if (labels[i] > 0) {
                labels[i] = find(labels[i])
            }
        }

        // Compact labels
        val remap = mutableMapOf<Int, Int>()
        var compactId = 0
        for (i in labels.indices) {
            if (labels[i] > 0) {
                if (labels[i] !in remap) {
                    compactId++
                    remap[labels[i]] = compactId
                }
                labels[i] = remap[labels[i]]!!
            }
        }

        return labels
    }

    // ─── Classification Pipeline ──────────────────────────────────

    /**
     * Classify text orientation and rotate 180° if needed.
     */
    private fun classifyAndRotate(cropped: Bitmap): Bitmap {
        val predictor = clsPredictor ?: return cropped

        val clsH = 48
        val clsW = 192

        val resized = Bitmap.createScaledBitmap(cropped, clsW, clsH, true)
        val inputData = bitmapToCHW(resized, MEAN, STD)

        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(1, 3, clsH.toLong(), clsW.toLong()))
        inputTensor.setData(inputData)
        predictor.run()

        val outputTensor = predictor.getOutput(0)
        val outputData = outputTensor.getFloatData()

        resized.recycle()

        // Check if text is rotated 180°
        // outputData[0] = score for 0°, outputData[1] = score for 180°
        if (outputData.size >= 2 && outputData[1] > outputData[0] && outputData[1] > 0.9f) {
            val matrix = Matrix()
            matrix.postRotate(180f)
            val rotated = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
            return rotated
        }

        return cropped
    }

    // ─── Recognition Pipeline ─────────────────────────────────────

    /**
     * Run text recognition on a cropped text region.
     */
    private fun runRecognition(bitmap: Bitmap): Pair<String, Float> {
        val predictor = recPredictor ?: throw IllegalStateException("Recognition model not loaded")

        // Resize to fixed height, variable width
        val imgH = REC_IMAGE_HEIGHT
        val ratio = imgH.toFloat() / bitmap.height
        var imgW = (bitmap.width * ratio).roundToInt()
        imgW = max(imgW, 10) // minimum width

        val resized = Bitmap.createScaledBitmap(bitmap, imgW, imgH, true)
        val inputData = bitmapToCHW(resized, MEAN, STD)

        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(1, 3, imgH.toLong(), imgW.toLong()))
        inputTensor.setData(inputData)
        predictor.run()

        val outputTensor = predictor.getOutput(0)
        val outputShape = outputTensor.shape()
        val outputData = outputTensor.getFloatData()

        resized.recycle()

        // CTC greedy decode
        return ctcGreedyDecode(outputData, outputShape)
    }

    /**
     * CTC greedy decoder: argmax along time axis, collapse duplicates, remove blanks.
     * Automatically reverses Arabic (RTL) text.
     */
    private fun ctcGreedyDecode(outputData: FloatArray, shape: LongArray): Pair<String, Float> {
        // shape: [1, timeSteps, numClasses]
        val timeSteps = shape[1].toInt()
        val numClasses = shape[2].toInt()

        val sb = StringBuilder()
        var totalConf = 0.0f
        var charCount = 0
        var prevIdx = 0 // blank index

        for (t in 0 until timeSteps) {
            var maxIdx = 0
            var maxVal = Float.MIN_VALUE
            for (c in 0 until numClasses) {
                val idx = t * numClasses + c
                if (idx < outputData.size && outputData[idx] > maxVal) {
                    maxVal = outputData[idx]
                    maxIdx = c
                }
            }

            // Skip blank (0) and duplicate consecutive
            if (maxIdx != 0 && maxIdx != prevIdx) {
                if (maxIdx < labelList.size) {
                    sb.append(labelList[maxIdx])
                    // Convert logit to confidence via softmax approximation
                    totalConf += maxVal
                    charCount++
                }
            }
            prevIdx = maxIdx
        }

        var text = sb.toString()

        // Reverse Arabic text: the CTC decoder reads left-to-right but Arabic is RTL.
        // If the text contains Arabic characters, reverse the entire string.
        if (containsArabic(text)) {
            text = text.reversed()
        }

        val avgConf = if (charCount > 0) totalConf / charCount else 0f
        // Clamp to [0, 1] — output may be logits or softmax depending on model
        val confidence = min(1.0f, max(0.0f, avgConf))

        return Pair(text, confidence)
    }

    /**
     * Check if a string contains Arabic characters.
     */
    private fun containsArabic(text: String): Boolean {
        return text.any { ch ->
            ch in ARABIC_RANGE_START..ARABIC_RANGE_END ||
            ch in ARABIC_EXT_A_START..ARABIC_EXT_A_END ||
            ch in ARABIC_PRESENTATION_A_START..ARABIC_PRESENTATION_A_END ||
            ch in ARABIC_PRESENTATION_B_START..ARABIC_PRESENTATION_B_END
        }
    }

    // ─── Image Utilities ──────────────────────────────────────────

    /**
     * Convert Bitmap to CHW float array with normalization.
     */
    private fun bitmapToCHW(bitmap: Bitmap, mean: FloatArray, std: FloatArray): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val floats = FloatArray(3 * h * w)
        for (i in 0 until h * w) {
            val pixel = pixels[i]
            floats[i] = (((pixel shr 16) and 0xFF) / 255.0f - mean[0]) / std[0]               // R channel
            floats[h * w + i] = (((pixel shr 8) and 0xFF) / 255.0f - mean[1]) / std[1]        // G channel
            floats[2 * h * w + i] = ((pixel and 0xFF) / 255.0f - mean[2]) / std[2]             // B channel
        }
        return floats
    }

    /**
     * Crop a text region from the source image using a bounding box.
     */
    private fun cropTextRegion(bitmap: Bitmap, box: List<FloatArray>): Bitmap? {
        // Get axis-aligned bounding rect from the quadrilateral
        val minX = max(0, box.minOf { it[0] }.roundToInt())
        val minY = max(0, box.minOf { it[1] }.roundToInt())
        val maxX = min(bitmap.width, box.maxOf { it[0] }.roundToInt())
        val maxY = min(bitmap.height, box.maxOf { it[1] }.roundToInt())

        val cropW = maxX - minX
        val cropH = maxY - minY

        if (cropW <= 0 || cropH <= 0) return null

        return try {
            Bitmap.createBitmap(bitmap, minX, minY, cropW, cropH)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to crop region: ${e.message}")
            null
        }
    }

    // ─── Asset Management ─────────────────────────────────────────

    private fun copyAssetToInternal(assetPath: String): String {
        val outFile = File(context.filesDir, assetPath)
        if (outFile.exists()) {
            Log.d(TAG, "Asset already exists: ${outFile.absolutePath}")
            return outFile.absolutePath
        }

        outFile.parentFile?.mkdirs()

        try {
            val inputStream = context.assets.open("flutter_assets/assets/$assetPath")
            FileOutputStream(outFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
        } catch (e: Exception) {
            try {
                val inputStream = context.assets.open(assetPath)
                FileOutputStream(outFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
            } catch (e2: Exception) {
                throw RuntimeException(
                    "Cannot copy asset '$assetPath' to internal storage. " +
                    "Ensure model files are placed in assets/models/ and assets/labels/. " +
                    "Error: ${e2.message}"
                )
            }
        }

        Log.d(TAG, "Copied asset to: ${outFile.absolutePath}")
        return outFile.absolutePath
    }

    private fun loadLabelList(path: String): List<String> {
        val labels = mutableListOf<String>()
        // Index 0 = blank token for CTC
        labels.add("")
        File(path).forEachLine { line ->
            labels.add(line.trim())
        }
        // Add space token at the end
        labels.add(" ")
        return labels
    }
}
