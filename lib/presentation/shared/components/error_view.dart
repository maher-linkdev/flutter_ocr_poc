import 'package:flutter/material.dart';

import '../../../core/error/failures.dart';

/// A reusable error display with icon, message, and retry button.
class ErrorView extends StatelessWidget {
  final Failure failure;
  final VoidCallback? onRetry;

  const ErrorView({
    required this.failure,
    this.onRetry,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              _getIcon(),
              size: 64,
              color: theme.colorScheme.error,
            ),
            const SizedBox(height: 16),
            Text(
              _getTitle(),
              style: theme.textTheme.titleMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              failure.message,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
              textAlign: TextAlign.center,
            ),
            if (onRetry != null) ...[
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: onRetry,
                icon: const Icon(Icons.refresh),
                label: const Text('Try Again'),
              ),
            ],
          ],
        ),
      ),
    );
  }

  IconData _getIcon() {
    if (failure is OcrInitFailure) return Icons.settings_suggest;
    if (failure is ModelNotFoundFailure) return Icons.folder_off;
    if (failure is OcrRecognitionFailure) return Icons.text_fields;
    if (failure is ImageInputFailure) return Icons.broken_image;
    if (failure is PermissionFailure) return Icons.block;
    return Icons.error_outline;
  }

  String _getTitle() {
    if (failure is OcrInitFailure) return 'Engine Error';
    if (failure is ModelNotFoundFailure) return 'Models Missing';
    if (failure is OcrRecognitionFailure) return 'Recognition Failed';
    if (failure is ImageInputFailure) return 'Image Error';
    if (failure is PermissionFailure) return 'Permission Denied';
    return 'Something Went Wrong';
  }
}
