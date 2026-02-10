import 'bounding_box.dart';

/// A single recognized text block from OCR.
///
/// Each text block contains the recognized [text], its spatial
/// [boundingBox] in the image, and a [confidence] score (0.0–1.0).
class TextBlock {
  /// The recognized text string.
  final String text;

  /// Spatial location in the source image.
  final BoundingBox boundingBox;

  /// Recognition confidence score (0.0 to 1.0).
  final double confidence;

  const TextBlock({
    required this.text,
    required this.boundingBox,
    required this.confidence,
  });

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is TextBlock &&
        other.text == text &&
        other.boundingBox == boundingBox &&
        other.confidence == confidence;
  }

  @override
  int get hashCode => Object.hash(text, boundingBox, confidence);
}
