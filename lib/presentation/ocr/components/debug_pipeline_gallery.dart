import 'dart:io';

import 'package:flutter/material.dart';

/// Displays debug pipeline images saved during OCR recognition.
///
/// Shows a detection overlay followed by per-region pipeline stages
/// (cropped, trimmed, preprocessed, CLAHE) in an
/// expandable gallery. Tapping any image opens a full-screen viewer.
class DebugPipelineGallery extends StatefulWidget {
  final String debugImageDir;

  const DebugPipelineGallery({required this.debugImageDir, super.key});

  @override
  State<DebugPipelineGallery> createState() => _DebugPipelineGalleryState();
}

class _DebugPipelineGalleryState extends State<DebugPipelineGallery> {
  bool _expanded = false;

  static const _stageLabels = {
    '01_cropped': 'Cropped',
    '02_trimmed': 'Trimmed',
    '03_preprocessed': 'Preprocessed',
    '04_clahe': 'CLAHE',
  };

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dir = Directory(widget.debugImageDir);
    if (!dir.existsSync()) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        InkWell(
          onTap: () => setState(() => _expanded = !_expanded),
          borderRadius: BorderRadius.circular(8),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              children: [
                Icon(
                  Icons.bug_report_outlined,
                  size: 20,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 8),
                Text(
                  'Pipeline Debug Images',
                  style: theme.textTheme.titleSmall,
                ),
                const Spacer(),
                Icon(
                  _expanded ? Icons.expand_less : Icons.expand_more,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ],
            ),
          ),
        ),
        if (_expanded) _buildGalleryContent(theme, dir),
      ],
    );
  }

  Widget _buildGalleryContent(ThemeData theme, Directory dir) {
    final children = <Widget>[];

    // Detection overlay — shown at a fixed height, not inside a horizontal list
    final overlay = File('${dir.path}/detection_overlay.png');
    if (overlay.existsSync()) {
      children.add(
        _SectionHeader(label: 'Detection Overlay', theme: theme),
      );
      children.add(
        GestureDetector(
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) =>
                  _DebugImageViewer(file: overlay, label: 'Detection Overlay'),
            ),
          ),
          child: Container(
            height: 200,
            width: double.infinity,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: theme.colorScheme.outlineVariant),
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(7),
              child: Image.file(
                overlay,
                fit: BoxFit.contain,
                errorBuilder: (_, __, ___) => const Center(
                  child: Icon(Icons.broken_image_outlined, size: 24),
                ),
              ),
            ),
          ),
        ),
      );
      children.add(const SizedBox(height: 16));
    }

    // Region directories
    final regionDirs = dir
        .listSync()
        .whereType<Directory>()
        .where((d) => d.path.split('/').last.startsWith('region_'))
        .toList()
      ..sort((a, b) => a.path.compareTo(b.path));

    for (final regionDir in regionDirs) {
      final regionName = regionDir.path.split('/').last;
      final regionIndex = regionName.replaceFirst('region_', '');
      children.add(
        _SectionHeader(label: 'Region $regionIndex', theme: theme),
      );

      final stageFiles = regionDir
          .listSync()
          .whereType<File>()
          .where((f) => f.path.endsWith('.png'))
          .toList()
        ..sort((a, b) => a.path.compareTo(b.path));

      if (stageFiles.isNotEmpty) {
        children.add(
          SizedBox(
            height: 120,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: stageFiles.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final file = stageFiles[index];
                final stem = file.path
                    .split('/')
                    .last
                    .replaceFirst('.png', '');
                final label = _stageLabels[stem] ?? stem;
                return _ImageTile(file: file, label: label);
              },
            ),
          ),
        );
      }
      children.add(const SizedBox(height: 12));
    }

    if (children.isEmpty) {
      children.add(
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Text(
            'No debug images found',
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: children,
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String label;
  final ThemeData theme;

  const _SectionHeader({required this.label, required this.theme});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        label,
        style: theme.textTheme.labelMedium?.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }
}

class _ImageTile extends StatelessWidget {
  final File file;
  final String label;

  const _ImageTile({required this.file, required this.label});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return GestureDetector(
      onTap: () => _openViewer(context),
      child: SizedBox(
        width: 120,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(
              height: 96,
              width: 120,
              child: Container(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                    color: theme.colorScheme.outlineVariant,
                  ),
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(7),
                  child: Image.file(
                    file,
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => const Center(
                      child: Icon(Icons.broken_image_outlined, size: 24),
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }

  void _openViewer(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => _DebugImageViewer(file: file, label: label),
      ),
    );
  }
}

class _DebugImageViewer extends StatelessWidget {
  final File file;
  final String label;

  const _DebugImageViewer({required this.file, required this.label});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: Text(label),
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
      ),
      body: Center(
        child: InteractiveViewer(
          minScale: 0.5,
          maxScale: 5.0,
          child: Image.file(
            file,
            fit: BoxFit.contain,
            errorBuilder: (_, __, ___) => const Icon(
              Icons.broken_image_outlined,
              color: Colors.white54,
              size: 64,
            ),
          ),
        ),
      ),
    );
  }
}
