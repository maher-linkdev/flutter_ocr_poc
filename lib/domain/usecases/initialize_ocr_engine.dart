import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';
import '../repositories/ocr_repository.dart';
import 'base_usecase.dart';

/// Use case to initialize the PaddleOCR engine.
///
/// Loads detection and recognition models
/// from bundled assets into memory for offline inference.
class InitializeOcrEngine implements UseCase<bool, NoParams> {
  final OcrRepository repository;

  const InitializeOcrEngine(this.repository);

  @override
  Future<Either<Failure, bool>> call(NoParams params) {
    return repository.initializeEngine();
  }
}
