/// Base failure class for domain-level errors.
///
/// All failures are data, not exceptions. They flow through
/// `Either<Failure, T>` from data → application → presentation.
abstract class Failure {
  final String message;
  final String? code;

  const Failure(this.message, {this.code});

  @override
  String toString() => 'Failure($code): $message';
}

/// OCR engine initialization failure.
class OcrInitFailure extends Failure {
  const OcrInitFailure([super.message = 'Failed to initialize OCR engine'])
      : super(code: 'OCR_INIT_ERROR');
}

/// OCR recognition/processing failure.
class OcrRecognitionFailure extends Failure {
  const OcrRecognitionFailure([super.message = 'OCR recognition failed'])
      : super(code: 'OCR_RECOGNITION_ERROR');
}

/// Model files not found or corrupted.
class ModelNotFoundFailure extends Failure {
  const ModelNotFoundFailure([super.message = 'OCR model files not found'])
      : super(code: 'MODEL_NOT_FOUND');
}

/// Image input failure (camera, gallery, file system).
class ImageInputFailure extends Failure {
  const ImageInputFailure([super.message = 'Failed to load image'])
      : super(code: 'IMAGE_INPUT_ERROR');
}

/// Permission denied failure.
class PermissionFailure extends Failure {
  const PermissionFailure([super.message = 'Permission denied'])
      : super(code: 'PERMISSION_DENIED');
}

/// Unknown/unexpected errors.
class UnknownFailure extends Failure {
  const UnknownFailure([super.message = 'Something went wrong'])
      : super(code: 'UNKNOWN');
}
