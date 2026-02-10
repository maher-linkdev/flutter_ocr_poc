import 'bounding_box_model.dart';

/// Data transfer object for a single text block from the native layer.
class TextBlockModel {
  final String text;
  final BoundingBoxModel boundingBox;
  final double confidence;

  const TextBlockModel({
    required this.text,
    required this.boundingBox,
    required this.confidence,
  });

  /// Creates a [TextBlockModel] from native platform data.
  factory TextBlockModel.fromMap(Map<String, dynamic> map) {
    return TextBlockModel(
      text: map['text'] as String,
      boundingBox:
          BoundingBoxModel.fromMap(Map<String, dynamic>.from(map['boundingBox'] as Map)),
      confidence: (map['confidence'] as num).toDouble(),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'text': text,
      'boundingBox': boundingBox.toMap(),
      'confidence': confidence,
    };
  }
}
