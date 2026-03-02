import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../application/ocr/logic/ocr_providers.dart';
import '../../../domain/value_objects/ocr_engine_type.dart';
import '../../shared/components/error_view.dart';
import '../../shared/components/loading_indicator.dart';
import '../components/bounding_box_overlay.dart';
import '../components/image_source_selector.dart';
import '../components/ocr_result_view.dart';

/// Main OCR page — the primary screen of the application.
///
/// Allows users to select an image, run OCR, and view results
/// with bounding boxes overlaid on the source image.
class OcrPage extends ConsumerStatefulWidget {
  const OcrPage({super.key});

  @override
  ConsumerState<OcrPage> createState() => _OcrPageState();
}

class _OcrPageState extends ConsumerState<OcrPage> {
  @override
  void initState() {
    super.initState();
    // Initialize the OCR engine on first load.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _initEngine();
    });
  }

  Future<void> _initEngine() async {
    await ref.read(ocrProvider.notifier).initializeEngine();
  }

  Future<void> _switchEngine(OcrEngineType newEngine) async {
    final current = ref.read(ocrEngineTypeProvider);
    if (current == newEngine) return;

    await ref.read(ocrProvider.notifier).disposeEngine();
    ref.read(ocrEngineTypeProvider.notifier).state = newEngine;
    await _initEngine();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(ocrProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('OCR'),
        centerTitle: true,
        actions: [
          if (state.hasResult || state.hasImage)
            IconButton(
              icon: const Icon(Icons.refresh),
              tooltip: 'Clear',
              onPressed: () => ref.read(ocrProvider.notifier).clearResult(),
            ),
        ],
      ),
      body: _buildBody(state, theme),
      floatingActionButton: _buildFab(state),
    );
  }

  Widget _buildBody(dynamic state, ThemeData theme) {
    // Engine initializing
    if (state.isInitializing) {
      return const LoadingIndicator(
        message: 'Initializing OCR engine...',
      );
    }

    // Engine initialization failed
    if (!state.isEngineReady && state.hasError) {
      return ErrorView(
        failure: state.failure!,
        onRetry: () => ref.read(ocrProvider.notifier).initializeEngine(),
      );
    }

    // Engine not ready (shouldn't happen normally)
    if (!state.isEngineReady && !state.isInitializing) {
      return const LoadingIndicator(
        message: 'Waiting for OCR engine...',
      );
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // ─── OCR Engine Selector ───
          _buildEngineSelector(theme),

          const SizedBox(height: 16),

          // ─── Image Preview ───
          _buildImageSection(state, theme),

          const SizedBox(height: 16),

          // ─── Recognize Button ───
          if (state.hasImage && !state.isProcessing)
            FilledButton.icon(
              onPressed: state.canRecognize
                  ? () => ref.read(ocrProvider.notifier).recognizeText()
                  : null,
              icon: const Icon(Icons.document_scanner),
              label: const Text('Recognize Text'),
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 14),
              ),
            ),

          // ─── Processing indicator ───
          if (state.isProcessing)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: LoadingIndicator(
                message: 'Running OCR...',
              ),
            ),

          // ─── Error ───
          if (state.hasError && state.isEngineReady)
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: ErrorView(
                failure: state.failure!,
                onRetry: state.canRecognize
                    ? () => ref.read(ocrProvider.notifier).recognizeText()
                    : null,
              ),
            ),

          // ─── Results ───
          if (state.hasResult) ...[
            const SizedBox(height: 24),
            OcrResultView(result: state.result!),
          ],
        ],
      ),
    );
  }

  Widget _buildEngineSelector(ThemeData theme) {
    final engineType = ref.watch(ocrEngineTypeProvider);
    final state = ref.watch(ocrProvider);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'OCR Engine',
              style: theme.textTheme.titleSmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 8),
            SegmentedButton<OcrEngineType>(
              segments: OcrEngineType.values
                  .map((e) => ButtonSegment<OcrEngineType>(
                        value: e,
                        label: Text(e.displayName),
                        icon: Icon(e == OcrEngineType.paddle ? Icons.abc : Icons.text_fields),
                      ))
                  .toList(),
              selected: {engineType},
              onSelectionChanged: (selected) {
                if (state.isLoading) return;
                final newEngine = selected.first;
                _switchEngine(newEngine);
              },
            ),
            const SizedBox(height: 4),
            Text(
              engineType.description,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.8),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildImageSection(dynamic state, ThemeData theme) {
    if (!state.hasImage) {
      return _buildImagePlaceholder(theme);
    }

    // Show image with bounding box overlay if results available
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Container(
        constraints: const BoxConstraints(maxHeight: 350),
        decoration: BoxDecoration(
          border: Border.all(
            color: theme.colorScheme.outlineVariant,
          ),
          borderRadius: BorderRadius.circular(12),
        ),
        child: state.hasResult
            ? BoundingBoxOverlay(
                imageFile: state.selectedImage!,
                ocrResult: state.result!,
              )
            : Image.file(
                state.selectedImage!,
                fit: BoxFit.contain,
              ),
      ),
    );
  }

  Widget _buildImagePlaceholder(ThemeData theme) {
    return GestureDetector(
      onTap: () => _showImageSourceSelector(),
      child: Container(
        height: 250,
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: theme.colorScheme.outlineVariant,
            style: BorderStyle.solid,
          ),
        ),
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.add_photo_alternate_outlined,
                size: 64,
                color: theme.colorScheme.onSurfaceVariant,
              ),
              const SizedBox(height: 12),
              Text(
                'Tap to select an image',
                style: theme.textTheme.bodyLarge?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'Camera, Gallery, or File System',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant.withValues(
                    alpha: 0.7,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget? _buildFab(dynamic state) {
    if (!state.isEngineReady || state.isProcessing) return null;

    return FloatingActionButton.extended(
      onPressed: _showImageSourceSelector,
      icon: const Icon(Icons.add_a_photo),
      label: const Text('Select Image'),
    );
  }

  void _showImageSourceSelector() {
    ImageSourceSelector.show(
      context,
      onSourceSelected: (source) {
        ref.read(ocrProvider.notifier).selectImage(source);
      },
    );
  }
}
