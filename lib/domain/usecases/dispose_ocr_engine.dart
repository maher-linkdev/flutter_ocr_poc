import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';
import '../repositories/ocr_repository.dart';
import 'base_usecase.dart';

/// Use case to release OCR engine resources.
///
/// Should be called when OCR is no longer needed to free
/// native memory held by Paddle Lite predictors.
class DisposeOcrEngine implements UseCase<bool, NoParams> {
  final OcrRepository repository;

  const DisposeOcrEngine(this.repository);

  @override
  Future<Either<Failure, bool>> call(NoParams params) {
    return repository.disposeEngine();
  }
}
