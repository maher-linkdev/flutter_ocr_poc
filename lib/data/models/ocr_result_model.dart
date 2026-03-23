import 'text_block_model.dart';

/// Data transfer object for the complete OCR result from the native layer.
class OcrResultModel {
  final List<TextBlockModel> textBlocks;
  final int processingTimeMs;
  final String imagePath;
  final String? debugImageDir;

  const OcrResultModel({
    required this.textBlocks,
    required this.processingTimeMs,
    required this.imagePath,
    this.debugImageDir,
  });

  /// Creates an [OcrResultModel] from native platform data.
  factory OcrResultModel.fromMap(Map<String, dynamic> map) {
    final blocks = (map['textBlocks'] as List<dynamic>)
        .map((block) =>
            TextBlockModel.fromMap(Map<String, dynamic>.from(block as Map)))
        .toList();

    return OcrResultModel(
      textBlocks: blocks,
      processingTimeMs: map['processingTimeMs'] as int,
      imagePath: map['imagePath'] as String,
      debugImageDir: map['debugImageDir'] as String?,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'textBlocks': textBlocks.map((b) => b.toMap()).toList(),
      'processingTimeMs': processingTimeMs,
      'imagePath': imagePath,
      if (debugImageDir != null) 'debugImageDir': debugImageDir,
    };
  }
}
