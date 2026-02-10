import 'dart:io';

import '../../../core/error/failures.dart';
import '../../../domain/entities/ocr_result.dart';

/// Immutable state for the OCR feature.
///
/// Tracks engine initialization, image selection, OCR processing,
/// and the resulting text blocks.
class OcrState {
  /// Whether the OCR engine is initialized and ready.
  final bool isEngineReady;

  /// Whether the engine is currently initializing.
  final bool isInitializing;

  /// Whether OCR recognition is in progress.
  final bool isProcessing;

  /// The currently selected image file.
  final File? selectedImage;

  /// The OCR result from the last recognition.
  final OcrResult? result;

  /// Current failure, if any.
  final Failure? failure;

  const OcrState({
    this.isEngineReady = false,
    this.isInitializing = false,
    this.isProcessing = false,
    this.selectedImage,
    this.result,
    this.failure,
  });

  /// Initial state — nothing loaded.
  factory OcrState.initial() => const OcrState();

  // --------------- Computed Properties ---------------

  /// Whether an image is selected and ready for OCR.
  bool get hasImage => selectedImage != null;

  /// Whether a result is available.
  bool get hasResult => result != null;

  /// Whether there is an active failure.
  bool get hasError => failure != null;

  /// Whether any loading operation is in progress.
  bool get isLoading => isInitializing || isProcessing;

  /// Whether the user can trigger OCR (engine ready + image selected + not busy).
  bool get canRecognize => isEngineReady && hasImage && !isProcessing;

  // --------------- copyWith ---------------

  OcrState copyWith({
    bool? isEngineReady,
    bool? isInitializing,
    bool? isProcessing,
    File? selectedImage,
    OcrResult? result,
    Failure? failure,
    bool clearImage = false,
    bool clearResult = false,
    bool clearFailure = false,
  }) {
    return OcrState(
      isEngineReady: isEngineReady ?? this.isEngineReady,
      isInitializing: isInitializing ?? this.isInitializing,
      isProcessing: isProcessing ?? this.isProcessing,
      selectedImage: clearImage ? null : (selectedImage ?? this.selectedImage),
      result: clearResult ? null : (result ?? this.result),
      failure: clearFailure ? null : (failure ?? this.failure),
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is OcrState &&
        other.isEngineReady == isEngineReady &&
        other.isInitializing == isInitializing &&
        other.isProcessing == isProcessing &&
        other.selectedImage == selectedImage &&
        other.result == result &&
        other.failure == failure;
  }

  @override
  int get hashCode => Object.hash(
        isEngineReady,
        isInitializing,
        isProcessing,
        selectedImage,
        result,
        failure,
      );
}
