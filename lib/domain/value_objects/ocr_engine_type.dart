/// OCR engine backend selection.
///
/// - [paddle]: PaddleOCR via Paddle Lite — best for **Arabic** (with arabic_PP-OCRv5 model).
/// - [mlkit]: Google ML Kit Text Recognition v2 — on-device, offline. Supports Latin, Chinese,
///   Devanagari, Japanese, Korean. **Does NOT support Arabic.**
enum OcrEngineType {
  paddle,
  mlkit,
}

extension OcrEngineTypeX on OcrEngineType {
  String get displayName {
    switch (this) {
      case OcrEngineType.paddle:
        return 'PaddleOCR';
      case OcrEngineType.mlkit:
        return 'ML Kit v2';
    }
  }

  String get description {
    switch (this) {
      case OcrEngineType.paddle:
        return 'Arabic, multilingual (offline)';
      case OcrEngineType.mlkit:
        return 'Latin, Chinese, etc. (offline, no Arabic)';
    }
  }
}
