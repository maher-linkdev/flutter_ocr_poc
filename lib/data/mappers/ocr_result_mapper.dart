import '../../domain/entities/bounding_box.dart';
import '../../domain/entities/ocr_result.dart';
import '../../domain/entities/text_block.dart';
import '../models/bounding_box_model.dart';
import '../models/ocr_result_model.dart';
import '../models/text_block_model.dart';

/// Converts between data models (DTOs) and domain entities.
abstract class OcrResultMapper {
  OcrResultMapper._();

  /// Converts [OcrResultModel] (DTO) to [OcrResult] (Entity).
  static OcrResult toEntity(OcrResultModel model) {
    return OcrResult(
      textBlocks: model.textBlocks.map(_textBlockToEntity).toList(),
      processingTimeMs: model.processingTimeMs,
      imagePath: model.imagePath,
    );
  }

  /// Converts [TextBlockModel] to [TextBlock] entity.
  static TextBlock _textBlockToEntity(TextBlockModel model) {
    return TextBlock(
      text: model.text,
      boundingBox: _boundingBoxToEntity(model.boundingBox),
      confidence: model.confidence,
    );
  }

  /// Converts [BoundingBoxModel] to [BoundingBox] entity.
  static BoundingBox _boundingBoxToEntity(BoundingBoxModel model) {
    return BoundingBox(
      topLeft: model.topLeft,
      topRight: model.topRight,
      bottomRight: model.bottomRight,
      bottomLeft: model.bottomLeft,
    );
  }
}
