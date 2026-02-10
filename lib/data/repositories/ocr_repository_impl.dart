import 'dart:io';

import 'package:dartz/dartz.dart';

import '../../core/error/exceptions.dart';
import '../../core/error/failures.dart';
import '../../domain/entities/ocr_result.dart';
import '../../domain/repositories/ocr_repository.dart';
import '../datasources/native/ocr_native_datasource.dart';
import '../mappers/ocr_result_mapper.dart';

/// Repository implementation for OCR operations.
///
/// Catches data layer exceptions and converts them to domain failures
/// via `Either<Failure, T>`.
class OcrRepositoryImpl implements OcrRepository {
  final OcrNativeDataSource _nativeDataSource;

  OcrRepositoryImpl({
    required OcrNativeDataSource nativeDataSource,
  }) : _nativeDataSource = nativeDataSource;

  @override
  Future<Either<Failure, bool>> initializeEngine() async {
    try {
      final result = await _nativeDataSource.initializeEngine();
      return Right(result);
    } on OcrInitException catch (e) {
      return Left(OcrInitFailure(e.message));
    } on ModelNotFoundException catch (e) {
      return Left(ModelNotFoundFailure(e.message));
    } on PlatformChannelException catch (e) {
      return Left(OcrInitFailure(e.message));
    } catch (e) {
      return Left(UnknownFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, OcrResult>> recognizeText(File image) async {
    try {
      final model = await _nativeDataSource.recognizeText(image);
      final entity = OcrResultMapper.toEntity(model);
      return Right(entity);
    } on OcrRecognitionException catch (e) {
      return Left(OcrRecognitionFailure(e.message));
    } on ImageInputException catch (e) {
      return Left(ImageInputFailure(e.message));
    } on PlatformChannelException catch (e) {
      return Left(OcrRecognitionFailure(e.message));
    } catch (e) {
      return Left(UnknownFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, bool>> disposeEngine() async {
    try {
      final result = await _nativeDataSource.disposeEngine();
      return Right(result);
    } catch (e) {
      return Left(UnknownFailure(e.toString()));
    }
  }
}
