import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../../domain/usecases/base_usecase.dart';
import '../../../domain/usecases/dispose_ocr_engine.dart';
import '../../../domain/usecases/initialize_ocr_engine.dart';
import '../../../domain/usecases/recognize_text.dart';
import '../../../domain/value_objects/image_source_type.dart';
import '../../../core/error/failures.dart';
import 'ocr_state.dart';

/// State notifier managing the OCR feature lifecycle.
///
/// Orchestrates engine initialization, image selection, and
/// OCR recognition through domain use cases.
class OcrNotifier extends StateNotifier<OcrState> {
  final InitializeOcrEngine _initializeOcrEngine;
  final RecognizeText _recognizeText;
  final DisposeOcrEngine _disposeOcrEngine;
  final ImagePicker _imagePicker;

  OcrNotifier({
    required InitializeOcrEngine initializeOcrEngine,
    required RecognizeText recognizeText,
    required DisposeOcrEngine disposeOcrEngine,
    ImagePicker? imagePicker,
  })  : _initializeOcrEngine = initializeOcrEngine,
        _recognizeText = recognizeText,
        _disposeOcrEngine = disposeOcrEngine,
        _imagePicker = imagePicker ?? ImagePicker(),
        super(OcrState.initial());

  /// Initialize the OCR engine. Should be called on app start.
  Future<void> initializeEngine() async {
    state = state.copyWith(isInitializing: true, clearFailure: true);

    final result = await _initializeOcrEngine(const NoParams());

    state = result.fold(
      (failure) => state.copyWith(
        isInitializing: false,
        isEngineReady: false,
        failure: failure,
      ),
      (_) => state.copyWith(
        isInitializing: false,
        isEngineReady: true,
      ),
    );
  }

  /// Select an image from the specified [source].
  Future<void> selectImage(ImageSourceType source) async {
    state = state.copyWith(clearFailure: true, clearResult: true);

    try {
      File? imageFile;

      switch (source) {
        case ImageSourceType.camera:
          final picked = await _imagePicker.pickImage(
            source: ImageSource.camera,
            imageQuality: 100,
          );
          if (picked != null) {
            imageFile = File(picked.path);
          }
          break;

        case ImageSourceType.gallery:
          final picked = await _imagePicker.pickImage(
            source: ImageSource.gallery,
            imageQuality: 100,
          );
          if (picked != null) {
            imageFile = File(picked.path);
          }
          break;

        case ImageSourceType.fileSystem:
          final result = await FilePicker.platform.pickFiles(
            type: FileType.image,
            allowMultiple: false,
          );
          if (result != null && result.files.single.path != null) {
            imageFile = File(result.files.single.path!);
          }
          break;
      }

      if (imageFile != null) {
        state = state.copyWith(selectedImage: imageFile);
      }
    } catch (e) {
      state = state.copyWith(
        failure: ImageInputFailure('Failed to pick image: $e'),
      );
    }
  }

  /// Run OCR on the currently selected image.
  Future<void> recognizeText() async {
    if (!state.canRecognize) return;

    state = state.copyWith(isProcessing: true, clearFailure: true);

    final result = await _recognizeText(state.selectedImage!);

    state = result.fold(
      (failure) => state.copyWith(
        isProcessing: false,
        failure: failure,
      ),
      (ocrResult) => state.copyWith(
        isProcessing: false,
        result: ocrResult,
      ),
    );
  }

  /// Clear the current result and selected image.
  void clearResult() {
    state = state.copyWith(clearResult: true, clearImage: true);
  }

  /// Clear the current failure.
  void clearFailure() {
    state = state.copyWith(clearFailure: true);
  }

  /// Release engine resources.
  Future<void> disposeEngine() async {
    await _disposeOcrEngine(const NoParams());
    state = state.copyWith(isEngineReady: false);
  }
}
