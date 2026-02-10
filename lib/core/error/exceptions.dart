/// Base exception for the data layer.
///
/// DataSources throw typed exceptions. Repositories catch them
/// and convert to [Failure] via `Either<Failure, T>`.
abstract class AppException implements Exception {
  final String message;
  final dynamic originalError;

  const AppException(this.message, {this.originalError});

  @override
  String toString() => '$runtimeType: $message';
}

/// Thrown when the OCR engine fails to initialize.
class OcrInitException extends AppException {
  const OcrInitException([super.message = 'Failed to initialize OCR engine']);
}

/// Thrown when OCR recognition fails during inference.
class OcrRecognitionException extends AppException {
  const OcrRecognitionException([super.message = 'OCR recognition failed']);
}

/// Thrown when model files are missing or corrupted.
class ModelNotFoundException extends AppException {
  const ModelNotFoundException([super.message = 'OCR model files not found']);
}

/// Thrown when the native platform channel call fails.
class PlatformChannelException extends AppException {
  const PlatformChannelException(
      [super.message = 'Platform channel communication failed']);
}

/// Thrown when image input fails.
class ImageInputException extends AppException {
  const ImageInputException([super.message = 'Failed to load image']);
}
