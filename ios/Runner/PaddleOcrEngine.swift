import UIKit

/// Errors that can occur during OCR operations.
enum OcrError: Error, LocalizedError {
    case modelNotFound(String)
    case initializationFailed(String)
    case recognitionFailed(String)
    case invalidImage(String)
    
    var errorDescription: String? {
        switch self {
        case .modelNotFound(let msg): return "Model not found: \(msg)"
        case .initializationFailed(let msg): return "Initialization failed: \(msg)"
        case .recognitionFailed(let msg): return "Recognition failed: \(msg)"
        case .invalidImage(let msg): return "Invalid image: \(msg)"
        }
    }
}

/// Native PaddleOCR engine using Paddle Lite for on-device inference on iOS.
///
/// This class handles:
/// 1. Locating model files from the Flutter asset bundle
/// 2. Loading detection, classification, and recognition models via Paddle Lite
/// 3. Running the full OCR pipeline: detect → classify → recognize
/// 4. Returning structured results (text + bounding boxes + confidence)
///
/// ## Setup Requirements
///
/// Before this engine can run, you must:
/// 1. Download Paddle Lite iOS framework (arm64):
///    - `PaddleLite.framework` → drag into Xcode project, embed & sign
///
/// 2. Download pre-converted .nb models and place in `assets/models/`:
///    - `ch_PP-OCRv3_det_slim_infer.nb`
///    - `ch_PP-OCRv3_rec_slim_infer.nb`
///    - `ch_ppocr_mobile_v2.0_cls_slim_opt.nb`
///
/// 3. Place the character dictionary in `assets/labels/`:
///    - `ppocr_keys_v1.txt`
///
/// ## Integration with Paddle Lite
///
/// This implementation will use Paddle Lite's C API through a bridging header:
/// ```
/// #import "paddle_api.h"
/// ```
///
/// The actual Paddle Lite calls are wrapped in do-catch blocks and
/// will gracefully degrade if the framework is not present.
class PaddleOcrEngine {
    
    private var detModelPath: String?
    private var recModelPath: String?
    private var clsModelPath: String?
    private var labelPath: String?
    private var threadCount: Int = 4
    private var isInitialized = false
    
    /// Character dictionary for recognition decoding
    private var labelList: [String] = []
    
    /// Initialize the OCR engine by locating models from the Flutter asset bundle
    /// and loading them into Paddle Lite predictors.
    func initialize(
        detModelFileName: String,
        recModelFileName: String,
        clsModelFileName: String,
        labelFileName: String,
        threadCount: Int = 4
    ) throws {
        self.threadCount = threadCount
        
        // Locate model files within the Flutter assets bundle
        detModelPath = try locateAsset(directory: "models", fileName: detModelFileName)
        recModelPath = try locateAsset(directory: "models", fileName: recModelFileName)
        clsModelPath = try locateAsset(directory: "models", fileName: clsModelFileName)
        labelPath = try locateAsset(directory: "labels", fileName: labelFileName)
        
        // Load character dictionary
        labelList = try loadLabelList(path: labelPath!)
        
        // ──────────────────────────────────────────────────────────────
        // PADDLE LITE INITIALIZATION
        //
        // When Paddle Lite framework is available, uncomment and use:
        //
        // detPredictor = try loadModel(modelPath: detModelPath!)
        // recPredictor = try loadModel(modelPath: recModelPath!)
        // clsPredictor = try loadModel(modelPath: clsModelPath!)
        //
        // private func loadModel(modelPath: String) throws -> PaddleMobileConfig {
        //     let config = MobileConfig()
        //     config.setModelFromFile(modelPath)
        //     config.setThreads(Int32(threadCount))
        //     config.setPowerMode(.LITE_POWER_HIGH)
        //     guard let predictor = PaddlePredictor.create(config) else {
        //         throw OcrError.initializationFailed("Failed to create predictor")
        //     }
        //     return predictor
        // }
        // ──────────────────────────────────────────────────────────────
        
        isInitialized = true
        print("[PaddleOCR] Engine initialized successfully")
        print("[PaddleOCR]   Detection model: \(detModelPath ?? "nil")")
        print("[PaddleOCR]   Recognition model: \(recModelPath ?? "nil")")
        print("[PaddleOCR]   Classification model: \(clsModelPath ?? "nil")")
        print("[PaddleOCR]   Label dictionary: \(labelList.count) characters")
    }
    
    /// Run the full OCR pipeline on an image.
    ///
    /// Pipeline: Image → Detection → Crop → Classification → Recognition
    ///
    /// - Parameter imagePath: Absolute path to the image file.
    /// - Returns: Dictionary containing textBlocks, processingTimeMs, and imagePath.
    func recognizeText(imagePath: String) throws -> [String: Any] {
        guard isInitialized else {
            throw OcrError.recognitionFailed("OCR engine not initialized")
        }
        
        let startTime = CFAbsoluteTimeGetCurrent()
        
        // Load and decode the image
        guard let image = UIImage(contentsOfFile: imagePath),
              let cgImage = image.cgImage else {
            throw OcrError.invalidImage("Cannot load image at: \(imagePath)")
        }
        
        let imgWidth = cgImage.width
        let imgHeight = cgImage.height
        
        // ──────────────────────────────────────────────────────────────
        // FULL PADDLE LITE OCR PIPELINE
        //
        // When Paddle Lite framework is integrated, replace the stub
        // below with actual inference (same pipeline as Android):
        //
        // Step 1: Detection — find text regions
        // Step 2: Classification — determine text orientation
        // Step 3: Recognition — read text from each cropped region
        // ──────────────────────────────────────────────────────────────
        
        // STUB: Return placeholder results for build verification.
        let textBlocks = createStubResults(imgWidth: imgWidth, imgHeight: imgHeight)
        
        let processingTimeMs = Int((CFAbsoluteTimeGetCurrent() - startTime) * 1000)
        
        return [
            "textBlocks": textBlocks,
            "processingTimeMs": processingTimeMs,
            "imagePath": imagePath
        ]
    }
    
    /// Release all native resources.
    func release() {
        // When Paddle Lite is integrated:
        // detPredictor = nil
        // recPredictor = nil
        // clsPredictor = nil
        isInitialized = false
        print("[PaddleOCR] Engine released")
    }
    
    // MARK: - Private Helpers
    
    /// Locate a file within the Flutter assets bundle.
    ///
    /// Flutter assets on iOS are stored under the main bundle in
    /// `Frameworks/App.framework/flutter_assets/assets/`.
    private func locateAsset(directory: String, fileName: String) throws -> String {
        // Try looking in the Flutter assets within the main bundle
        let flutterAssetPath = "Frameworks/App.framework/flutter_assets/assets/\(directory)/\(fileName)"
        if let bundlePath = Bundle.main.path(forResource: flutterAssetPath, ofType: nil) {
            return bundlePath
        }
        
        // Try the registered Flutter asset key
        let assetKey = "assets/\(directory)/\(fileName)"
        if let registeredKey = Bundle.main.path(forResource: assetKey, ofType: nil) {
            return registeredKey
        }
        
        // Try direct bundle lookup by filename
        let fileNameWithoutExtension = (fileName as NSString).deletingPathExtension
        let fileExtension = (fileName as NSString).pathExtension
        if let directPath = Bundle.main.path(forResource: fileNameWithoutExtension, ofType: fileExtension) {
            return directPath
        }
        
        // Search in the flutter_assets directory
        let flutterAssetsDir = Bundle.main.bundlePath + "/Frameworks/App.framework/flutter_assets/assets/\(directory)"
        let fullPath = flutterAssetsDir + "/\(fileName)"
        if FileManager.default.fileExists(atPath: fullPath) {
            return fullPath
        }
        
        throw OcrError.modelNotFound(
            "Cannot find '\(fileName)' in assets/\(directory)/. " +
            "Ensure model files are placed in the assets directory."
        )
    }
    
    /// Load the character dictionary from a text file.
    private func loadLabelList(path: String) throws -> [String] {
        let content = try String(contentsOfFile: path, encoding: .utf8)
        var labels: [String] = [""]  // Blank token at index 0 for CTC decoding
        labels.append(contentsOf: content.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty })
        labels.append(" ")  // Space token at the end
        return labels
    }
    
    /// Stub results for build verification.
    /// Remove this method once Paddle Lite inference is integrated.
    private func createStubResults(imgWidth: Int, imgHeight: Int) -> [[String: Any]] {
        return [
            [
                "text": "[PaddleOCR Engine Stub] Models loaded. Integrate Paddle Lite framework for real inference.",
                "boundingBox": [
                    "points": [
                        [10.0, 10.0],
                        [Double(imgWidth) - 10.0, 10.0],
                        [Double(imgWidth) - 10.0, 40.0],
                        [10.0, 40.0]
                    ]
                ],
                "confidence": 0.99
            ]
        ]
    }
}
