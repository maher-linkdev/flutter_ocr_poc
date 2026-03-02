import 'dart:io';
import 'dart:ui' as ui;

import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';

import '../../../core/error/exceptions.dart';
import '../../models/bounding_box_model.dart';
import '../../models/ocr_result_model.dart';
import '../../models/text_block_model.dart';
import '../native/ocr_native_datasource.dart';

/// ML Kit Text Recognition v2 data source.
///
/// On-device, offline. Supports Latin, Chinese, Devanagari, Japanese, Korean.
/// **Does NOT support Arabic** — use PaddleOCR for Arabic.
///
/// See: https://developers.google.com/ml-kit/vision/text-recognition/v2
class MlkOcrDataSource implements OcrNativeDataSource {
  TextRecognizer? _recognizer;
  final TextRecognitionScript _script;

  MlkOcrDataSource({
    TextRecognitionScript script = TextRecognitionScript.latin,
  }) : _script = script;

  @override
  Future<bool> initializeEngine() async {
    try {
      _recognizer?.close();
      _recognizer = TextRecognizer(script: _script);
      return true;
    } catch (e) {
      throw OcrInitException('ML Kit init failed: $e');
    }
  }

  @override
  Future<OcrResultModel> recognizeText(File image) async {
    if (!await image.exists()) {
      throw const ImageInputException('Image file does not exist');
    }

    final recognizer = _recognizer;
    if (recognizer == null) {
      throw const OcrInitException('ML Kit not initialized. Call initializeEngine first.');
    }

    final stopwatch = Stopwatch()..start();

    try {
      final inputImage = InputImage.fromFilePath(image.path);
      final recognizedText = await recognizer.processImage(inputImage);

      stopwatch.stop();

      final textBlocks = <TextBlockModel>[];
      for (final block in recognizedText.blocks) {
        final rect = block.boundingBox;
        final confidence = _blockConfidence(block);
        final boundingBox = _rectToBoundingBox(rect);
        textBlocks.add(TextBlockModel(
          text: block.text,
          boundingBox: boundingBox,
          confidence: confidence,
        ));
      }

      return OcrResultModel(
        textBlocks: textBlocks,
        processingTimeMs: stopwatch.elapsedMilliseconds,
        imagePath: image.path,
      );
    } catch (e) {
      throw OcrRecognitionException('ML Kit recognition failed: $e');
    }
  }

  @override
  Future<bool> disposeEngine() async {
    try {
      _recognizer?.close();
      _recognizer = null;
      return true;
    } catch (e) {
      return false;
    }
  }

  double _blockConfidence(TextBlock block) {
    if (block.lines.isEmpty) return 0.85;
    var sum = 0.0;
    var count = 0;
    for (final line in block.lines) {
      if (line.confidence != null) {
        sum += line.confidence!;
        count++;
      }
    }
    return count > 0 ? sum / count : 0.85;
  }

  BoundingBoxModel _rectToBoundingBox(ui.Rect rect) {
    return BoundingBoxModel(
      topLeft: [rect.left, rect.top],
      topRight: [rect.right, rect.top],
      bottomRight: [rect.right, rect.bottom],
      bottomLeft: [rect.left, rect.bottom],
    );
  }
}
