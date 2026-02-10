import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../../../domain/entities/ocr_result.dart';
import '../../../domain/entities/text_block.dart';

/// Overlays bounding boxes on top of the source image.
///
/// Draws colored rectangles around each detected text region
/// and shows the recognized text above each box.
class BoundingBoxOverlay extends StatelessWidget {
  final File imageFile;
  final OcrResult ocrResult;

  const BoundingBoxOverlay({
    required this.imageFile,
    required this.ocrResult,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        return FutureBuilder<ui.Image>(
          future: _loadImage(),
          builder: (context, snapshot) {
            if (!snapshot.hasData) {
              return const Center(child: CircularProgressIndicator());
            }

            final image = snapshot.data!;
            final imageWidth = image.width.toDouble();
            final imageHeight = image.height.toDouble();

            // Calculate scale to fit image in container
            final scaleX = constraints.maxWidth / imageWidth;
            final scaleY = constraints.maxHeight / imageHeight;
            final scale = scaleX < scaleY ? scaleX : scaleY;

            final displayWidth = imageWidth * scale;
            final displayHeight = imageHeight * scale;

            return Center(
              child: SizedBox(
                width: displayWidth,
                height: displayHeight,
                child: Stack(
                  children: [
                    // Source image
                    Image.file(
                      imageFile,
                      width: displayWidth,
                      height: displayHeight,
                      fit: BoxFit.contain,
                    ),
                    // Bounding box painter
                    CustomPaint(
                      size: Size(displayWidth, displayHeight),
                      painter: _BoundingBoxPainter(
                        textBlocks: ocrResult.textBlocks,
                        scaleX: scale,
                        scaleY: scale,
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  Future<ui.Image> _loadImage() async {
    final bytes = await imageFile.readAsBytes();
    final codec = await ui.instantiateImageCodec(bytes);
    final frame = await codec.getNextFrame();
    return frame.image;
  }
}

class _BoundingBoxPainter extends CustomPainter {
  final List<TextBlock> textBlocks;
  final double scaleX;
  final double scaleY;

  _BoundingBoxPainter({
    required this.textBlocks,
    required this.scaleX,
    required this.scaleY,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.blue.withValues(alpha: 0.6)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.0;

    final fillPaint = Paint()
      ..color = Colors.blue.withValues(alpha: 0.1)
      ..style = PaintingStyle.fill;

    for (final block in textBlocks) {
      final box = block.boundingBox;
      final path = Path()
        ..moveTo(box.topLeft[0] * scaleX, box.topLeft[1] * scaleY)
        ..lineTo(box.topRight[0] * scaleX, box.topRight[1] * scaleY)
        ..lineTo(box.bottomRight[0] * scaleX, box.bottomRight[1] * scaleY)
        ..lineTo(box.bottomLeft[0] * scaleX, box.bottomLeft[1] * scaleY)
        ..close();

      canvas.drawPath(path, fillPaint);
      canvas.drawPath(path, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _BoundingBoxPainter oldDelegate) {
    return oldDelegate.textBlocks != textBlocks;
  }
}
