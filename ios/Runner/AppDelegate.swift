import Flutter
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate {
    
    private var ocrMethodHandler: OcrMethodHandler?
    
    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        GeneratedPluginRegistrant.register(with: self)
        
        // Register PaddleOCR MethodChannel
        let controller = window?.rootViewController as! FlutterViewController
        let channel = FlutterMethodChannel(
            name: "com.example.flutter_ocr_poc/ocr",
            binaryMessenger: controller.binaryMessenger
        )
        
        ocrMethodHandler = OcrMethodHandler()
        channel.setMethodCallHandler { [weak self] (call, result) in
            self?.ocrMethodHandler?.handle(call: call, result: result)
        }
        
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
}
