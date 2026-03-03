package com.example.flutter_ocr_poc.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import com.example.flutter_ocr_poc.ocr.preprocessing.PreprocessConfig
import com.example.flutter_ocr_poc.ocr.preprocessing.TextCropPreprocessor
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

        // Arabic-Indic digit ranges (LTR — must NOT trigger RTL reversal)
        private const val ARABIC_INDIC_DIGIT_START = '\u0660'   // ٠
        private const val ARABIC_INDIC_DIGIT_END = '\u0669'     // ٩
        private const val EXTENDED_ARABIC_INDIC_DIGIT_START = '\u06F0' // ۰
        private const val EXTENDED_ARABIC_INDIC_DIGIT_END = '\u06F9'   // ۹

        // Recognition (PP-OCRv5 default is height 48; 32 is also supported but 48 often better)
        private const val REC_IMAGE_HEIGHT = 48

        // Detection normalization (ImageNet)
        private val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        // Recognition normalization (PP-OCRv5 uses mean=0.5, std=0.5)
        private val REC_MEAN = floatArrayOf(0.5f, 0.5f, 0.5f)
        private val REC_STD = floatArrayOf(0.5f, 0.5f, 0.5f)
    }

    private var detPredictor: PaddlePredictor? = null
    private var recPredictor: PaddlePredictor? = null
    private var recOnnxRunner: RecOnnxRunner? = null
    private var clsPredictor: PaddlePredictor? = null

    private var detModelPath: String? = null
    private var recModelPath: String? = null
    private var clsModelPath: String? = null
    private var labelPath: String? = null
    private var threadCount: Int = 4
    private var enableContrastEnhance: Boolean = false
    private var enablePreprocessing: Boolean = false
    private var isInitialized = false

    private var labelList: List<String> = emptyList()
    private val claheProcessor = ClaheProcessor()
    private var textCropPreprocessor: TextCropPreprocessor? = null

    /**
     * Initialize the OCR engine by copying models and loading predictors.
     *
     * @param recOnnxFileName If set, use ONNX Runtime for PaddleOCR Arabic rec.
     */
    fun initialize(
        detModelFileName: String,
        recModelFileName: String,
        clsModelFileName: String,
        labelFileName: String,
        threadCount: Int = 4,
        enableContrastEnhance: Boolean = false,
        recOnnxFileName: String? = null,
        enablePreprocessing: Boolean = false,
        superResModelFileName: String? = null
    ) {
        this.threadCount = threadCount
        this.enableContrastEnhance = enableContrastEnhance
        this.enablePreprocessing = enablePreprocessing

        // Copy assets to internal storage
        detModelPath = copyAssetToInternal("$MODELS_DIR/$detModelFileName")
        clsModelPath = copyAssetToInternal("$MODELS_DIR/$clsModelFileName")
        labelPath = copyAssetToInternal("$LABELS_DIR/$labelFileName")

        // Load character dictionary
        labelList = loadLabelList(labelPath!!)

        // Load Paddle Lite predictors (det + cls always; rec only when not using ONNX)
        detPredictor = loadModel(detModelPath!!)
        clsPredictor = loadModel(clsModelPath!!)

        if (recOnnxFileName != null) {
            // PaddleOCR Arabic rec via ONNX Runtime
            val recOnnxPath = copyAssetToInternal("$MODELS_DIR/$recOnnxFileName")
            recOnnxRunner = RecOnnxRunner(recOnnxPath)
            recModelPath = recOnnxPath
            Log.i(TAG, "  Recognition model (PaddleOCR ONNX): $recOnnxPath")
        } else {
            // Legacy: Paddle Lite .nb for rec
            recModelPath = copyAssetToInternal("$MODELS_DIR/$recModelFileName")
            recPredictor = loadModel(recModelPath!!)
            Log.i(TAG, "  Recognition model (Paddle Lite): $recModelPath")
        }

        // Initialize preprocessing pipeline
        if (enablePreprocessing) {
            val superResPath = if (superResModelFileName != null) {
                try {
                    copyAssetToInternal("$MODELS_DIR/$superResModelFileName")
                } catch (e: Exception) {
                    Log.w(TAG, "Super-res model not found, SR disabled: ${e.message}")
                    null
                }
            } else null

            val preprocessConfig = PreprocessConfig(
                superResModelPath = superResPath
            )
            textCropPreprocessor = TextCropPreprocessor(preprocessConfig).also {
                it.initialize()
            }
        }

        isInitialized = true
        Log.i(TAG, "OCR Engine initialized successfully")
        Log.i(TAG, "  Detection model: $detModelPath")
        Log.i(TAG, "  Classification model: $clsModelPath")
        Log.i(TAG, "  Label dictionary: ${labelList.size} characters")
        if (enableContrastEnhance) {
            Log.i(TAG, "  CLAHE contrast enhancement: enabled")
        }
        if (enablePreprocessing) {
            Log.i(TAG, "  Preprocessing pipeline: enabled")
        }
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

                // Trim excess background from detection unclip expansion
                val trimmed = trimCropBackground(cropped)
                if (trimmed !== cropped) cropped.recycle()

                // Classify text orientation and rotate if needed
                val oriented = classifyAndRotate(trimmed)

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
        textCropPreprocessor?.close()
        textCropPreprocessor = null
        recOnnxRunner?.close()
        recOnnxRunner = null
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

        // Convert to CHW float array with ImageNet normalization (detection)
        val inputData = bitmapToCHW(resized, DET_MEAN, DET_STD)

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
        val inputData = bitmapToCHW(resized, DET_MEAN, DET_STD)

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
     * Enhance contrast for recognition using CLAHE (Contrast Limited Adaptive Histogram Equalization).
     * Much better than global stretch for documents with uneven lighting (ID cards, shadowed text).
     */
    private fun enhanceContrastForRecognition(bitmap: Bitmap): Bitmap {
        return claheProcessor.process(bitmap)
    }

    /**
     * Run text recognition on a cropped text region.
     * Priority: PaddleOCR ONNX → Paddle Lite.
     */
    private fun runRecognition(bitmap: Bitmap): Pair<String, Float> {
        val onnxRunner = recOnnxRunner
        val predictor = recPredictor

        if (onnxRunner != null) {
            return runRecognitionOnnx(bitmap, onnxRunner)
        }
        if (predictor != null) {
            return runRecognitionPaddle(bitmap, predictor)
        }
        throw IllegalStateException("Recognition model not loaded")
    }

    private fun runRecognitionOnnx(bitmap: Bitmap, runner: RecOnnxRunner): Pair<String, Float> {
        var toRecognize = bitmap

        // Preprocessing pipeline (before CLAHE)
        val preprocessor = textCropPreprocessor
        if (preprocessor != null) {
            val preprocessed = preprocessor.process(toRecognize)
            if (preprocessed !== toRecognize && toRecognize !== bitmap) toRecognize.recycle()
            toRecognize = preprocessed
        }

        if (enableContrastEnhance) {
            val enhanced = enhanceContrastForRecognition(toRecognize)
            if (enhanced !== toRecognize && toRecognize !== bitmap) toRecognize.recycle()
            toRecognize = enhanced
        }

        val imgH = REC_IMAGE_HEIGHT
        val ratio = imgH.toFloat() / toRecognize.height
        var imgW = (toRecognize.width * ratio).roundToInt()
        imgW = max(imgW, 10)

        val resized = Bitmap.createScaledBitmap(toRecognize, imgW, imgH, true)
        if (toRecognize != bitmap) toRecognize.recycle()
        val inputData = bitmapToCHW(resized, REC_MEAN, REC_STD)

        val (outputData, outputShape) = runner.run(inputData, imgH, imgW)
        resized.recycle()

        return ctcGreedyDecode(outputData, outputShape)
    }

    private fun runRecognitionPaddle(bitmap: Bitmap, predictor: PaddlePredictor): Pair<String, Float> {
        var toRecognize = bitmap

        // Preprocessing pipeline (before CLAHE)
        val preprocessor = textCropPreprocessor
        if (preprocessor != null) {
            val preprocessed = preprocessor.process(toRecognize)
            if (preprocessed !== toRecognize && toRecognize !== bitmap) toRecognize.recycle()
            toRecognize = preprocessed
        }

        if (enableContrastEnhance) {
            val enhanced = enhanceContrastForRecognition(toRecognize)
            if (enhanced !== toRecognize && toRecognize !== bitmap) toRecognize.recycle()
            toRecognize = enhanced
        }

        val imgH = REC_IMAGE_HEIGHT
        val ratio = imgH.toFloat() / toRecognize.height
        var imgW = (toRecognize.width * ratio).roundToInt()
        imgW = max(imgW, 10)

        val resized = Bitmap.createScaledBitmap(toRecognize, imgW, imgH, true)
        if (toRecognize != bitmap) toRecognize.recycle()
        val inputData = bitmapToCHW(resized, REC_MEAN, REC_STD)

        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(1, 3, imgH.toLong(), imgW.toLong()))
        inputTensor.setData(inputData)
        predictor.run()

        val outputTensor = predictor.getOutput(0)
        val outputShape = outputTensor.shape()
        val outputData = outputTensor.getFloatData()
        resized.recycle()

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
                    totalConf += maxVal
                    charCount++
                }
            }
            prevIdx = maxIdx
        }

        var text = sb.toString()

        Log.d(TAG, "CTC decoded ($charCount chars): '$text'")

        // Smart Arabic RTL reversal: CTC decoder reads left-to-right but Arabic is RTL.
        // Reverse overall run order and Arabic letter runs, but keep digit sequences intact.
        if (containsArabic(text)) {
            text = smartArabicReverse(text)
        }

        val avgConf = if (charCount > 0) totalConf / charCount else 0f
        // Clamp to [0, 1] — output may be logits or softmax depending on model
        val confidence = min(1.0f, max(0.0f, avgConf))

        return Pair(text, confidence)
    }

    /**
     * Check if a character is an Arabic-Indic digit (U+0660–U+0669 or U+06F0–U+06F9).
     * These are LTR even in Arabic text and should not trigger RTL reversal.
     */
    private fun isArabicIndicDigit(ch: Char): Boolean {
        return ch in ARABIC_INDIC_DIGIT_START..ARABIC_INDIC_DIGIT_END ||
               ch in EXTENDED_ARABIC_INDIC_DIGIT_START..EXTENDED_ARABIC_INDIC_DIGIT_END
    }

    /**
     * Check if a character is an Arabic letter (not a digit).
     */
    private fun isArabicLetter(ch: Char): Boolean {
        if (isArabicIndicDigit(ch)) return false
        return ch in ARABIC_RANGE_START..ARABIC_RANGE_END ||
               ch in ARABIC_EXT_A_START..ARABIC_EXT_A_END ||
               ch in ARABIC_PRESENTATION_A_START..ARABIC_PRESENTATION_A_END ||
               ch in ARABIC_PRESENTATION_B_START..ARABIC_PRESENTATION_B_END
    }

    /**
     * Check if a string contains Arabic letters (excluding Arabic-Indic digits).
     */
    private fun containsArabic(text: String): Boolean {
        return text.any { isArabicLetter(it) }
    }

    /**
     * Smart reversal for Arabic text that preserves LTR digit sequences.
     *
     * Splits text into runs of Arabic letters vs non-Arabic (digits, spaces, punctuation).
     * Reverses Arabic letter runs and overall run order, but keeps digit/non-Arabic runs intact.
     *
     * Example: CTC outputs "٥٤٣٢١ مقر" → split into ["٥٤٣٢١", " ", "مقر"]
     *   → reverse Arabic runs: ["٥٤٣٢١", " ", "رقم"]
     *   → reverse run order: ["رقم", " ", "٥٤٣٢١"]
     *   → join: "رقم ١٢٣٤٥" ... wait, digits are already LTR in each run
     *   Actually: "رقم ٥٤٣٢١" but the digit run "٥٤٣٢١" was already in CTC (visual) order.
     *   Since CTC reads L→R, the digit run is already correct, so just reverse run order + Arabic runs.
     */
    private fun smartArabicReverse(text: String): String {
        // Split into runs: each run is either all-Arabic-letters or all-non-Arabic
        val runs = mutableListOf<String>()
        val currentRun = StringBuilder()
        var currentIsArabic: Boolean? = null

        for (ch in text) {
            val charIsArabic = isArabicLetter(ch)
            if (currentIsArabic != null && charIsArabic != currentIsArabic) {
                runs.add(currentRun.toString())
                currentRun.clear()
            }
            currentRun.append(ch)
            currentIsArabic = charIsArabic
        }
        if (currentRun.isNotEmpty()) {
            runs.add(currentRun.toString())
        }

        // Reverse Arabic letter runs (characters within the run), keep non-Arabic runs as-is
        val processedRuns = runs.map { run ->
            if (run.any { isArabicLetter(it) }) {
                run.reversed()
            } else {
                run
            }
        }

        // Reverse overall run order (RTL reordering)
        val result = processedRuns.reversed().joinToString("")
        Log.d(TAG, "Smart Arabic reverse: '$text' → '$result'")
        return result
    }

    // ─── Image Utilities ──────────────────────────────────────────

    /**
     * Convert Bitmap to CHW float array with normalization.
     * Outputs BGR channel order to match PaddleOCR's OpenCV-based training pipeline.
     */
    private fun bitmapToCHW(bitmap: Bitmap, mean: FloatArray, std: FloatArray): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val floats = FloatArray(3 * h * w)
        for (i in 0 until h * w) {
            val pixel = pixels[i]
            floats[i] = ((pixel and 0xFF) / 255.0f - mean[0]) / std[0]                        // B channel
            floats[h * w + i] = (((pixel shr 8) and 0xFF) / 255.0f - mean[1]) / std[1]        // G channel
            floats[2 * h * w + i] = (((pixel shr 16) and 0xFF) / 255.0f - mean[2]) / std[2]   // R channel
        }
        return floats
    }

    /**
     * Trim excess background from a cropped text region using Otsu thresholding
     * and row/column projection to find the tight content bounding box.
     * Returns the trimmed bitmap, or the original if trimming is not beneficial.
     */
    private fun trimCropBackground(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return bitmap

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Convert to grayscale luminance
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()
        }

        // Otsu threshold
        val histogram = IntArray(256)
        for (v in gray) histogram[v]++

        val total = w * h
        var sumAll = 0.0
        for (i in 0 until 256) sumAll += i * histogram[i].toDouble()

        var sumB = 0.0
        var wB = 0
        var maxVariance = 0.0
        var threshold = 128

        for (t in 0 until 256) {
            wB += histogram[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break

            sumB += t * histogram[t].toDouble()
            val meanB = sumB / wB
            val meanF = (sumAll - sumB) / wF
            val variance = wB.toDouble() * wF.toDouble() * (meanB - meanF) * (meanB - meanF)

            if (variance > maxVariance) {
                maxVariance = variance
                threshold = t
            }
        }

        // Create binary map (foreground = true where pixel is dark, i.e., ink)
        val binary = BooleanArray(w * h)
        for (i in gray.indices) {
            binary[i] = gray[i] <= threshold
        }

        // Row projection: find rows with foreground pixels
        var topRow = 0
        var bottomRow = h - 1
        for (y in 0 until h) {
            var hasFg = false
            for (x in 0 until w) {
                if (binary[y * w + x]) { hasFg = true; break }
            }
            if (hasFg) { topRow = y; break }
        }
        for (y in h - 1 downTo 0) {
            var hasFg = false
            for (x in 0 until w) {
                if (binary[y * w + x]) { hasFg = true; break }
            }
            if (hasFg) { bottomRow = y; break }
        }

        // Column projection: find columns with foreground pixels
        var leftCol = 0
        var rightCol = w - 1
        for (x in 0 until w) {
            var hasFg = false
            for (y in 0 until h) {
                if (binary[y * w + x]) { hasFg = true; break }
            }
            if (hasFg) { leftCol = x; break }
        }
        for (x in w - 1 downTo 0) {
            var hasFg = false
            for (y in 0 until h) {
                if (binary[y * w + x]) { hasFg = true; break }
            }
            if (hasFg) { rightCol = x; break }
        }

        // Add padding (2-4px)
        val pad = 3
        val trimLeft = max(0, leftCol - pad)
        val trimTop = max(0, topRow - pad)
        val trimRight = min(w, rightCol + pad + 1)
        val trimBottom = min(h, bottomRow + pad + 1)

        val trimW = trimRight - trimLeft
        val trimH = trimBottom - trimTop

        // Skip if result is too small
        if (trimW < 4 || trimH < 4) return bitmap

        // Skip if trimming is negligible (less than 5% reduction on any side)
        if (trimW >= w * 0.95f && trimH >= h * 0.95f) return bitmap

        Log.d(TAG, "Trim: ${w}x${h} → ${trimW}x${trimH} (removed L:$trimLeft T:$trimTop R:${w - trimRight} B:${h - trimBottom})")

        return try {
            Bitmap.createBitmap(bitmap, trimLeft, trimTop, trimW, trimH)
        } catch (e: Exception) {
            Log.w(TAG, "Trim failed: ${e.message}")
            bitmap
        }
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
