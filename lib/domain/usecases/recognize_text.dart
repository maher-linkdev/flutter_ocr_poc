import 'dart:io';

import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';
import '../entities/ocr_result.dart';
import '../repositories/ocr_repository.dart';
import 'base_usecase.dart';

/// Use case to perform OCR text recognition on an image.
///
/// Takes an image [File] and returns structured [OcrResult]
/// containing text blocks with bounding boxes and confidence.
class RecognizeText implements UseCase<OcrResult, File> {
  final OcrRepository repository;

  const RecognizeText(this.repository);

  @override
  Future<Either<Failure, OcrResult>> call(File image) {
    return repository.recognizeText(image);
  }
}
