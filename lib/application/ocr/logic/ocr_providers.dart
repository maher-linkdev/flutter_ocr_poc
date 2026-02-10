import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../data/datasources/native/ocr_native_datasource.dart';
import '../../../data/repositories/ocr_repository_impl.dart';
import '../../../domain/repositories/ocr_repository.dart';
import '../../../domain/usecases/dispose_ocr_engine.dart';
import '../../../domain/usecases/initialize_ocr_engine.dart';
import '../../../domain/usecases/recognize_text.dart';
import 'ocr_notifier.dart';
import 'ocr_state.dart';

// ─── Data Layer Providers ─────────────────────────────────────

/// Provides the native OCR data source.
final ocrNativeDataSourceProvider = Provider<OcrNativeDataSource>((ref) {
  return OcrNativeDataSourceImpl();
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
