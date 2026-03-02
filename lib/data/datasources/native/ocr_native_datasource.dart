import 'dart:io';

import 'package:flutter/services.dart';

import '../../../core/constants/app_constants.dart';
import '../../../core/error/exceptions.dart';
import '../../models/ocr_result_model.dart';

/// Interface for the native OCR data source.
abstract class OcrNativeDataSource {
  /// Initialize the native OCR engine.
  Future<bool> initializeEngine();

  /// Run OCR on the given image file path.
  Future<OcrResultModel> recognizeText(File image);

  /// Release native resources.
  Future<bool> disposeEngine();
}

/// Implementation that communicates with native Android/iOS
/// PaddleOCR via [MethodChannel].
class OcrNativeDataSourceImpl implements OcrNativeDataSource {
  final MethodChannel _channel;

  OcrNativeDataSourceImpl({MethodChannel? channel})
      : _channel = channel ??
            const MethodChannel(AppConstants.ocrMethodChannel);

  @override
  Future<bool> initializeEngine() async {
    try {
      final result = await _channel.invokeMethod<bool>(
        AppConstants.methodInitOcr,
        {
          'detModelFileName': AppConstants.detModelFileName,
          'recModelFileName': AppConstants.recModelFileName,
          'clsModelFileName': AppConstants.clsModelFileName,
          'labelFileName': AppConstants.labelFileName,
          'threadCount': AppConstants.defaultThreadCount,
          'enableContrastEnhance': AppConstants.enableRecContrastEnhance,
          if (AppConstants.recOnnxFileName != null)
            'recOnnxFileName': AppConstants.recOnnxFileName,
          'enablePreprocessing': AppConstants.enablePreprocessing,
          if (AppConstants.superResModelFileName != null)
            'superResModelFileName': AppConstants.superResModelFileName,
        },
      );
      return result ?? false;
    } on PlatformException catch (e) {
      throw OcrInitException(
        e.message ?? 'Failed to initialize OCR engine',
      );
    } on MissingPluginException {
      throw const PlatformChannelException(
        'OCR native plugin not registered. Ensure native code is configured.',
      );
    } catch (e) {
      throw OcrInitException('Unexpected error during OCR init: $e');
    }
  }

  @override
  Future<OcrResultModel> recognizeText(File image) async {
    if (!await image.exists()) {
      throw const ImageInputException('Image file does not exist');
    }

    try {
      final result = await _channel.invokeMapMethod<String, dynamic>(
        AppConstants.methodRecognizeText,
        {
          'imagePath': image.path,
        },
      );

      if (result == null) {
        throw const OcrRecognitionException('No result returned from OCR');
      }

      return OcrResultModel.fromMap(result);
    } on PlatformException catch (e) {
      throw OcrRecognitionException(
        e.message ?? 'OCR recognition failed',
      );
    } on MissingPluginException {
      throw const PlatformChannelException(
        'OCR native plugin not registered.',
      );
    } catch (e) {
      if (e is AppException) rethrow;
      throw OcrRecognitionException('Unexpected OCR error: $e');
    }
  }

  @override
  Future<bool> disposeEngine() async {
    try {
      final result = await _channel.invokeMethod<bool>(
        AppConstants.methodDispose,
      );
      return result ?? false;
    } on PlatformException catch (e) {
      throw PlatformChannelException(
        e.message ?? 'Failed to dispose OCR engine',
      );
    } catch (e) {
      return false;
    }
  }
}
