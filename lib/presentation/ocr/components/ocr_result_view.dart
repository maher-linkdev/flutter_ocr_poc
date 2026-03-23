import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../domain/entities/ocr_result.dart';
import 'debug_pipeline_gallery.dart';

/// Displays the OCR recognition results in a scrollable view.
///
/// Shows summary stats, full recognized text, and per-block details.
class OcrResultView extends StatelessWidget {
  final OcrResult result;

  const OcrResultView({
    required this.result,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // ─── Summary Stats ───
        _buildStatsRow(theme),
        const SizedBox(height: 16),

        // ─── Combined Text ───
        Row(
          children: [
            Text('Recognized Text', style: theme.textTheme.titleSmall),
            const Spacer(),
            if (result.hasText)
              IconButton(
                icon: const Icon(Icons.copy, size: 18),
                tooltip: 'Copy text',
                onPressed: () {
                  Clipboard.setData(
                    ClipboardData(text: result.combinedText),
                  );
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Text copied to clipboard'),
                      duration: Duration(seconds: 2),
                    ),
                  );
                },
              ),
          ],
        ),
        const SizedBox(height: 8),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(12),
          ),
          child: SelectableText(
            result.hasText ? result.combinedText : 'No text detected',
            style: theme.textTheme.bodyMedium?.copyWith(
              fontFamily: 'monospace',
              color: result.hasText
                  ? null
                  : theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
        const SizedBox(height: 16),

        // ─── Per-Block Details ───
        if (result.hasText) ...[
          Text(
            'Text Blocks (${result.blockCount})',
            style: theme.textTheme.titleSmall,
          ),
          const SizedBox(height: 8),
          ...result.textBlocks.asMap().entries.map(
                (entry) => _buildBlockCard(theme, entry.key, entry.value),
              ),
        ],

        // ─── Debug Pipeline Images ───
        if (result.debugImageDir != null) ...[
          const SizedBox(height: 16),
          DebugPipelineGallery(debugImageDir: result.debugImageDir!),
        ],
      ],
    );
  }

  Widget _buildStatsRow(ThemeData theme) {
    return Row(
      children: [
        _buildStatChip(
          theme,
          Icons.timer,
          '${result.processingTimeMs}ms',
          'Time',
        ),
        const SizedBox(width: 8),
        _buildStatChip(
          theme,
          Icons.text_fields,
          '${result.blockCount}',
          'Blocks',
        ),
        const SizedBox(width: 8),
        _buildStatChip(
          theme,
          Icons.verified,
          '${(result.averageConfidence * 100).toStringAsFixed(1)}%',
          'Confidence',
        ),
      ],
    );
  }

  Widget _buildStatChip(
    ThemeData theme,
    IconData icon,
    String value,
    String label,
  ) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
        decoration: BoxDecoration(
          color: theme.colorScheme.primaryContainer,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Column(
          children: [
            Icon(icon, size: 18, color: theme.colorScheme.onPrimaryContainer),
            const SizedBox(height: 4),
            Text(
              value,
              style: theme.textTheme.titleSmall?.copyWith(
                color: theme.colorScheme.onPrimaryContainer,
              ),
            ),
            Text(
              label,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onPrimaryContainer.withValues(
                  alpha: 0.7,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBlockCard(ThemeData theme, int index, dynamic block) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            CircleAvatar(
              radius: 14,
              backgroundColor: theme.colorScheme.primary,
              child: Text(
                '${index + 1}',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onPrimary,
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    block.text,
                    style: theme.textTheme.bodyMedium,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Confidence: ${(block.confidence * 100).toStringAsFixed(1)}%',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
