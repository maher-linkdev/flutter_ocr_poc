import 'dart:io';

import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';
import '../entities/ocr_result.dart';

/// Abstract repository interface for OCR operations.
///
/// Defined in the domain layer — implementations live in the data layer.
/// Returns `Either<Failure, T>` to make error handling explicit.
abstract class OcrRepository {
  /// Initialize the OCR engine with bundled models.
  ///
  /// Must be called before [recognizeText]. Returns [Right] with `true`
  /// on success, or [Left] with an appropriate [Failure].
  Future<Either<Failure, bool>> initializeEngine();

  /// Run OCR on the given [image] file.
  ///
  /// Returns an [OcrResult] containing all detected text blocks
  /// with bounding boxes and confidence scores.
  Future<Either<Failure, OcrResult>> recognizeText(File image);

  /// Release native OCR engine resources.
  Future<Either<Failure, bool>> disposeEngine();
}
