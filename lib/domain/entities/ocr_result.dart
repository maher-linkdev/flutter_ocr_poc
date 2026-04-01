import 'text_block.dart';

/// The complete result of an OCR recognition operation.
///
/// Contains a list of [textBlocks] found in the image, the total
/// [processingTimeMs] for the pipeline, and the full [combinedText].
class OcrResult {
  /// All recognized text blocks with positions and confidence.
  final List<TextBlock> textBlocks;

  /// Total processing time in milliseconds.
  final int processingTimeMs;

  /// Source image path that was processed.
  final String imagePath;

  /// Path to debug pipeline images directory, if debug saving was enabled.
  final String? debugImageDir;

  /// Path to the document-preprocessed image (after orientation + unwarp), if available.
  final String? preprocessedImagePath;

  /// Detection-stage image with bounding boxes drawn (same grid as OCR detection).
  final String? preprocessedWithBoxesImagePath;

  /// Document-level prep metadata when native layer reports it (e.g. Android).
  final int? docPrepRotationAngle;
  final bool? docPrepDidUnwarp;
  final int? docPrepProcessingTimeMs;

  const OcrResult({
    required this.textBlocks,
    required this.processingTimeMs,
    required this.imagePath,
    this.debugImageDir,
    this.preprocessedImagePath,
    this.preprocessedWithBoxesImagePath,
    this.docPrepRotationAngle,
    this.docPrepDidUnwarp,
    this.docPrepProcessingTimeMs,
  });

  /// Whether document-level orientation/unwarp was applied (native reported).
  bool get hasDocPrepMetadata =>
      docPrepRotationAngle != null &&
      docPrepDidUnwarp != null &&
      docPrepProcessingTimeMs != null;

  /// All recognized text combined into a single string.
  String get combinedText =>
      textBlocks.map((block) => block.text).join('\n');

  /// Whether any text was found.
  bool get hasText => textBlocks.isNotEmpty;

  /// Total number of text blocks detected.
  int get blockCount => textBlocks.length;

  /// Average confidence across all blocks.
  double get averageConfidence {
    if (textBlocks.isEmpty) return 0.0;
    final total =
        textBlocks.fold<double>(0.0, (sum, block) => sum + block.confidence);
    return total / textBlocks.length;
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is OcrResult &&
        other.processingTimeMs == processingTimeMs &&
        other.imagePath == imagePath &&
        other.textBlocks.length == textBlocks.length &&
        other.debugImageDir == debugImageDir &&
        other.preprocessedImagePath == preprocessedImagePath &&
        other.preprocessedWithBoxesImagePath == preprocessedWithBoxesImagePath &&
        other.docPrepRotationAngle == docPrepRotationAngle &&
        other.docPrepDidUnwarp == docPrepDidUnwarp &&
        other.docPrepProcessingTimeMs == docPrepProcessingTimeMs;
  }

  @override
  int get hashCode => Object.hash(
        textBlocks,
        processingTimeMs,
        imagePath,
        debugImageDir,
        preprocessedImagePath,
        preprocessedWithBoxesImagePath,
        docPrepRotationAngle,
        docPrepDidUnwarp,
        docPrepProcessingTimeMs,
      );
}
