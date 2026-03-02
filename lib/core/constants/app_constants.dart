/// Application-wide constants.
abstract class AppConstants {
  AppConstants._();

  /// MethodChannel name for OCR native communication.
  static const String ocrMethodChannel = 'com.example.flutter_ocr_poc/ocr';

  /// Native method names.
  static const String methodInitOcr = 'initOcr';
  static const String methodRecognizeText = 'recognizeText';
  static const String methodDispose = 'dispose';

  /// Model file names (Paddle Lite .nb format) — PP-OCRv5.
  static const String detModelFileName = 'PP-OCRv5_mobile_det.nb';
  /// Multilingual PP-OCRv5 rec (legacy Paddle Lite .nb).
  static const String recModelFileName = 'PP-OCRv5_mobile_rec.nb';
  static const String clsModelFileName = 'ch_ppocr_mobile_v2.0_cls_slim_opt.nb';

  /// Recognition via ONNX Runtime (PaddleOCR Arabic-specific model).
  /// Run `python scripts/convert_arabic_to_onnx.py` to generate the ONNX file.
  static const String? recOnnxFileName = 'arabic_PP-OCRv5_mobile_rec.onnx';

  /// Label/dictionary for recognition.
  static const String labelFileName = 'ppocr_keys_arabic_v5.txt';

  /// OCR engine configuration defaults.
  static const int defaultThreadCount = 4;
  static const int defaultMaxSideLen = 960;

  /// Enable CLAHE contrast enhancement before recognition.
  /// Uses adaptive local histogram equalization — much better than global
  /// stretch for documents with uneven lighting (ID cards, shadowed text).
  static const bool enableRecContrastEnhance = true;

  /// Enable the preprocessing pipeline (bilateral filter, unsharp mask,
  /// brightness normalization, and optional super-resolution) applied
  /// to each text crop before CLAHE and recognition.
  static const bool enablePreprocessing = true;

  /// ESPCN 2× super-resolution ONNX model for small text crops.
  /// Set to null to disable super-resolution while keeping other stages.
  static const String? superResModelFileName = 'espcn_x2.onnx';
  static const double defaultDetDbThresh = 0.3;
  static const double defaultDetDbBoxThresh = 0.6;
  static const double defaultDetDbUnclipRatio = 1.5;
  static const int defaultRecImageHeight = 48;
}
