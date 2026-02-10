import 'package:flutter/material.dart';

import '../../../domain/value_objects/image_source_type.dart';

/// Bottom sheet for selecting the image input source.
class ImageSourceSelector extends StatelessWidget {
  final ValueChanged<ImageSourceType> onSourceSelected;

  const ImageSourceSelector({
    required this.onSourceSelected,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  Text(
                    'Select Image Source',
                    style: theme.textTheme.titleMedium,
                  ),
                  const Spacer(),
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            ListTile(
              leading: const Icon(Icons.camera_alt),
              title: const Text('Camera'),
              subtitle: const Text('Take a photo'),
              onTap: () {
                Navigator.pop(context);
                onSourceSelected(ImageSourceType.camera);
              },
            ),
            ListTile(
              leading: const Icon(Icons.photo_library),
              title: const Text('Gallery'),
              subtitle: const Text('Pick from photos'),
              onTap: () {
                Navigator.pop(context);
                onSourceSelected(ImageSourceType.gallery);
              },
            ),
            ListTile(
              leading: const Icon(Icons.folder_open),
              title: const Text('File System'),
              subtitle: const Text('Browse files'),
              onTap: () {
                Navigator.pop(context);
                onSourceSelected(ImageSourceType.fileSystem);
              },
            ),
          ],
        ),
      ),
    );
  }

  /// Show this selector as a modal bottom sheet.
  static Future<void> show(
    BuildContext context, {
    required ValueChanged<ImageSourceType> onSourceSelected,
  }) {
    return showModalBottomSheet(
      context: context,
      builder: (_) => ImageSourceSelector(
        onSourceSelected: onSourceSelected,
      ),
    );
  }
}
