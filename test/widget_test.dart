import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:flutter_ocr_poc/main.dart';

void main() {
  testWidgets('App renders OCR page', (WidgetTester tester) async {
    await tester.pumpWidget(
      const ProviderScope(
        child: FlutterOcrApp(),
      ),
    );

    // The app should show the PaddleOCR title
    expect(find.text('PaddleOCR'), findsOneWidget);
  });
}
