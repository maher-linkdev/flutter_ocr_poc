import Flutter
import UIKit

/// Handles MethodChannel calls from Flutter for PaddleOCR operations on iOS.
///
/// Delegates actual OCR work to `PaddleOcrEngine` running on a
/// background dispatch queue to avoid blocking the main thread.
class OcrMethodHandler {
    
    private var ocrEngine: PaddleOcrEngine?
    private let backgroundQueue = DispatchQueue(label: "com.example.flutter_ocr_poc.ocr", qos: .userInitiated)
    
    func handle(call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "initOcr":
            handleInitOcr(call: call, result: result)
        case "recognizeText":
            handleRecognizeText(call: call, result: result)
        case "dispose":
            handleDispose(result: result)
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    /// Initialize the PaddleOCR engine with model files from the app bundle.
    ///
    /// Expected arguments:
    /// - detModelFileName: String — detection model .nb file
    /// - recModelFileName: String — recognition model .nb file
    /// - clsModelFileName: String — classification model .nb file
    /// - labelFileName: String — character dictionary file
    /// - threadCount: Int — number of CPU threads
    private func handleInitOcr(call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any] else {
            result(FlutterError(code: "INVALID_ARGS", message: "Arguments required", details: nil))
            return
        }
        
        guard let detModel = args["detModelFileName"] as? String,
              let recModel = args["recModelFileName"] as? String,
              let clsModel = args["clsModelFileName"] as? String,
              let labelFile = args["labelFileName"] as? String else {
            result(FlutterError(code: "INVALID_ARGS", message: "Model file names required", details: nil))
            return
        }
        
        let threadCount = args["threadCount"] as? Int ?? 4
        
        backgroundQueue.async { [weak self] in
            do {
                let engine = PaddleOcrEngine()
                try engine.initialize(
                    detModelFileName: detModel,
                    recModelFileName: recModel,
                    clsModelFileName: clsModel,
                    labelFileName: labelFile,
                    threadCount: threadCount
                )
                self?.ocrEngine = engine
                
                DispatchQueue.main.async {
                    result(true)
                }
            } catch {
                DispatchQueue.main.async {
                    result(FlutterError(
                        code: "OCR_INIT_ERROR",
                        message: error.localizedDescription,
                        details: nil
                    ))
                }
            }
        }
    }
    
    /// Run OCR text recognition on the provided image.
    ///
    /// Expected arguments:
    /// - imagePath: String — absolute path to the image file
    private func handleRecognizeText(call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let imagePath = args["imagePath"] as? String else {
            result(FlutterError(code: "INVALID_ARGS", message: "imagePath required", details: nil))
            return
        }
        
        guard let engine = ocrEngine else {
            result(FlutterError(
                code: "OCR_NOT_INITIALIZED",
                message: "OCR engine not initialized. Call initOcr first.",
                details: nil
            ))
            return
        }
        
        backgroundQueue.async {
            do {
                let ocrResult = try engine.recognizeText(imagePath: imagePath)
                
                DispatchQueue.main.async {
                    result(ocrResult)
                }
            } catch {
                DispatchQueue.main.async {
                    result(FlutterError(
                        code: "OCR_RECOGNITION_ERROR",
                        message: error.localizedDescription,
                        details: nil
                    ))
                }
            }
        }
    }
    
    /// Release all native OCR resources.
    private func handleDispose(result: @escaping FlutterResult) {
        ocrEngine?.release()
        ocrEngine = nil
        result(true)
    }
}
