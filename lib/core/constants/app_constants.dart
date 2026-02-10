/// Application-wide constants.
abstract class AppConstants {
  AppConstants._();

  /// MethodChannel name for OCR native communication.
  static const String ocrMethodChannel = 'com.example.flutter_ocr_poc/ocr';

  /// Native method names.
  static const String methodInitOcr = 'initOcr';
  static const String methodRecognizeText = 'recognizeText';
  static const String methodDispose = 'dispose';

  /// Model file names (Paddle Lite .nb format).
  static const String detModelFileName = 'ch_PP-OCRv3_det_slim_infer.nb';
  static const String recModelFileName = 'arabic_PP-OCRv3_rec_infer.nb';
  static const String clsModelFileName = 'ch_ppocr_mobile_v2.0_cls_slim_opt.nb';

  /// Label/dictionary file name for character recognition.
  static const String labelFileName = 'arabic_dict.txt';

  /// OCR engine configuration defaults.
  static const int defaultThreadCount = 4;
  static const int defaultMaxSideLen = 960;
  static const double defaultDetDbThresh = 0.3;
  static const double defaultDetDbBoxThresh = 0.6;
  static const double defaultDetDbUnclipRatio = 1.5;
  static const int defaultRecImageHeight = 48;
}
