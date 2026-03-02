import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../data/datasources/mlkit/mlkit_ocr_datasource.dart';
import '../../../data/datasources/native/ocr_native_datasource.dart';
import '../../../data/repositories/ocr_repository_impl.dart';
import '../../../domain/repositories/ocr_repository.dart';
import '../../../domain/usecases/dispose_ocr_engine.dart';
import '../../../domain/usecases/initialize_ocr_engine.dart';
import '../../../domain/usecases/recognize_text.dart';
import '../../../domain/value_objects/ocr_engine_type.dart';
import 'ocr_notifier.dart';
import 'ocr_state.dart';

// ─── Engine Selection ─────────────────────────────────────────

/// Selected OCR engine. Paddle supports Arabic; ML Kit supports Latin, Chinese, etc. (no Arabic).
final ocrEngineTypeProvider = StateProvider<OcrEngineType>((ref) => OcrEngineType.paddle);

// ─── Data Layer Providers ─────────────────────────────────────

/// Provides the OCR data source based on selected engine.
final ocrNativeDataSourceProvider = Provider<OcrNativeDataSource>((ref) {
  final engineType = ref.watch(ocrEngineTypeProvider);
  switch (engineType) {
    case OcrEngineType.paddle:
      return OcrNativeDataSourceImpl();
    case OcrEngineType.mlkit:
      return MlkOcrDataSource();
  }
});

// ─── Repository Provider ──────────────────────────────────────

/// Provides the OCR repository (interface typed for domain purity).
final ocrRepositoryProvider = Provider<OcrRepository>((ref) {
  return OcrRepositoryImpl(
    nativeDataSource: ref.watch(ocrNativeDataSourceProvider),
  );
});

// ─── UseCase Providers ────────────────────────────────────────

/// Provides the InitializeOcrEngine use case.
final initializeOcrEngineProvider = Provider<InitializeOcrEngine>((ref) {
  return InitializeOcrEngine(ref.watch(ocrRepositoryProvider));
});

/// Provides the RecognizeText use case.
final recognizeTextProvider = Provider<RecognizeText>((ref) {
  return RecognizeText(ref.watch(ocrRepositoryProvider));
});

/// Provides the DisposeOcrEngine use case.
final disposeOcrEngineProvider = Provider<DisposeOcrEngine>((ref) {
  return DisposeOcrEngine(ref.watch(ocrRepositoryProvider));
});

// ─── State Notifier Provider ──────────────────────────────────

/// Main OCR state provider. Exposes [OcrNotifier] and [OcrState].
final ocrProvider = StateNotifierProvider<OcrNotifier, OcrState>((ref) {
  return OcrNotifier(
    initializeOcrEngine: ref.watch(initializeOcrEngineProvider),
    recognizeText: ref.watch(recognizeTextProvider),
    disposeOcrEngine: ref.watch(disposeOcrEngineProvider),
  );
});

// ─── Derived / Computed Providers ─────────────────────────────

/// Whether the OCR engine is ready.
final isOcrEngineReadyProvider = Provider<bool>((ref) {
  return ref.watch(ocrProvider).isEngineReady;
});

/// Whether OCR is currently processing.
final isOcrProcessingProvider = Provider<bool>((ref) {
  return ref.watch(ocrProvider).isProcessing;
});

/// The current OCR result (nullable).
final ocrResultProvider = Provider((ref) {
  return ref.watch(ocrProvider).result;
});
