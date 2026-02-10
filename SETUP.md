# Flutter OCR POC — PaddleOCR Setup Guide

This project integrates **PaddleOCR** for offline, on-device OCR using **Paddle Lite** on both Android and iOS. The Dart side uses **DDD architecture** with **Riverpod** state management.

## Architecture Overview

```
┌─────────────────────────────────────────────┐
│              PRESENTATION                    │
│  OcrPage, BoundingBoxOverlay, ResultView    │
├─────────────────────────────────────────────┤
│              APPLICATION                     │
│  OcrNotifier, OcrState, Providers           │
├─────────────────────────────────────────────┤
│                DOMAIN                        │
│  OcrResult, TextBlock, BoundingBox          │
│  OcrRepository (interface), UseCases        │
├─────────────────────────────────────────────┤
│                  DATA                        │
│  Models (DTOs), Mappers, NativeDataSource   │
│  OcrRepositoryImpl                          │
├─────────────────────────────────────────────┤
│          NATIVE (MethodChannel)              │
│  Android: Kotlin + Paddle Lite Java API     │
│  iOS: Swift + Paddle Lite C API             │
└─────────────────────────────────────────────┘
```

## Prerequisites

- Flutter SDK 3.9.2+
- Android Studio / Xcode
- For Android: NDK r20b+ (for Paddle Lite native libs)
- For iOS: Xcode 14+ with arm64 support

## Step 1: Download Paddle Lite Libraries

### Android

1. Download Paddle Lite Android inference library (v2.13+):
   - Visit: https://github.com/PaddlePaddle/Paddle-Lite/releases
   - Download: `inference_lite_lib.android.armv8.gcc.c++_shared.with_extra.with_cv.tar.gz`

2. Extract and copy:
   ```bash
   # Copy the JNI shared library
   cp cxx/lib/libpaddle_light_api_shared.so \
      android/app/src/main/jniLibs/arm64-v8a/libpaddle_lite_jni.so

   # Copy the Java predictor JAR
   mkdir -p android/app/libs
   cp java/jar/PaddlePredictor.jar android/app/libs/
   ```

3. For armeabi-v7a support, also download the armv7 version and copy similarly.

### iOS

1. Download Paddle Lite iOS framework (v2.13+):
   - Visit: https://github.com/PaddlePaddle/Paddle-Lite/releases
   - Download: `inference_lite_lib.ios.armv8.with_cv.with_extra.with_log.tiny_publish.tar.gz`

2. Extract and add to Xcode project:
   - Drag `PaddleLite.framework` into `ios/Runner/` in Xcode
   - In target settings → "Frameworks, Libraries, and Embedded Content"
   - Set to "Embed & Sign"

3. Create a bridging header (`ios/Runner/Runner-Bridging-Header.h`):
   ```objc
   #import "paddle_api.h"
   ```

## Step 2: Download OCR Models

### Option A: PP-OCRv3 Slim (Recommended, ~5.9MB total)

Download pre-converted `.nb` models from the Paddle-Lite-Demo repository:

```bash
# Clone the demo repo
git clone -b feature/paddle-x https://github.com/PaddlePaddle/Paddle-Lite-Demo.git

# Models are in: Paddle-Lite-Demo/ocr/assets/models/
```

Or convert models yourself using `paddle_lite_opt`:

```bash
pip install paddlelite

# Detection model
paddle_lite_opt \
  --model_file=ch_PP-OCRv3_det_slim/inference.pdmodel \
  --param_file=ch_PP-OCRv3_det_slim/inference.pdiparams \
  --optimize_out=ch_PP-OCRv3_det_slim_infer \
  --valid_targets=arm \
  --optimize_out_type=naive_buffer

# Recognition model
paddle_lite_opt \
  --model_file=ch_PP-OCRv3_rec_slim/inference.pdmodel \
  --param_file=ch_PP-OCRv3_rec_slim/inference.pdiparams \
  --optimize_out=ch_PP-OCRv3_rec_slim_infer \
  --valid_targets=arm \
  --optimize_out_type=naive_buffer

# Classification model
paddle_lite_opt \
  --model_file=ch_ppocr_mobile_v2.0_cls_slim/inference.pdmodel \
  --param_file=ch_ppocr_mobile_v2.0_cls_slim/inference.pdiparams \
  --optimize_out=ch_ppocr_mobile_v2.0_cls_slim_opt \
  --valid_targets=arm \
  --optimize_out_type=naive_buffer
```

### Option B: PP-OCRv3 Full (~16.2MB total)

For higher accuracy, use the non-slim models. Follow the same conversion process with the full model files.

### Place Models

Copy the `.nb` files to the assets directory:

```bash
cp ch_PP-OCRv3_det_slim_infer.nb   assets/models/
cp ch_PP-OCRv3_rec_slim_infer.nb   assets/models/
cp ch_ppocr_mobile_v2.0_cls_slim_opt.nb assets/models/
```

## Step 3: Character Dictionary

The `assets/labels/ppocr_keys_v1.txt` file is included with basic ASCII characters. For full Chinese + multilingual support, download the complete dictionary:

```bash
# From PaddleOCR repository:
wget https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/ppocr_keys_v1.txt \
  -O assets/labels/ppocr_keys_v1.txt
```

## Step 4: Run the App

```bash
# Install Flutter dependencies
flutter pub get

# Run on Android
flutter run -d android

# Run on iOS
flutter run -d ios
```

## Step 5: Enable Real Inference

The native code currently returns **stub results** for build verification. Once you've completed Steps 1-3:

### Android (`PaddleOcrEngine.kt`)

1. Uncomment the Paddle Lite import and predictor initialization in `initialize()`
2. Replace `createStubResults()` call in `recognizeText()` with the actual pipeline
3. Uncomment the preprocessing and postprocessing utility methods

### iOS (`PaddleOcrEngine.swift`)

1. Add the Paddle Lite bridging header
2. Uncomment the predictor initialization in `initialize()`
3. Replace `createStubResults()` call in `recognizeText()` with the actual pipeline

## Project Structure

```
lib/
├── core/                          # App-wide utilities
│   ├── constants/app_constants.dart
│   ├── error/
│   │   ├── failures.dart          # Domain failures
│   │   └── exceptions.dart        # Data exceptions
│   └── utils/either_extensions.dart
│
├── domain/                        # Pure business logic
│   ├── entities/
│   │   ├── ocr_result.dart
│   │   ├── text_block.dart
│   │   └── bounding_box.dart
│   ├── repositories/
│   │   └── ocr_repository.dart    # Abstract interface
│   ├── usecases/
│   │   ├── base_usecase.dart
│   │   ├── initialize_ocr_engine.dart
│   │   ├── recognize_text.dart
│   │   └── dispose_ocr_engine.dart
│   └── value_objects/
│       └── image_source_type.dart
│
├── data/                          # Data layer implementation
│   ├── datasources/native/
│   │   └── ocr_native_datasource.dart  # MethodChannel bridge
│   ├── models/
│   │   ├── ocr_result_model.dart
│   │   ├── text_block_model.dart
│   │   └── bounding_box_model.dart
│   ├── mappers/
│   │   └── ocr_result_mapper.dart
│   └── repositories/
│       └── ocr_repository_impl.dart
│
├── application/                   # State management
│   └── ocr/logic/
│       ├── ocr_notifier.dart
│       ├── ocr_state.dart
│       └── ocr_providers.dart
│
├── presentation/                  # UI layer
│   ├── ocr/
│   │   ├── pages/ocr_page.dart
│   │   └── components/
│   │       ├── image_source_selector.dart
│   │       ├── ocr_result_view.dart
│   │       └── bounding_box_overlay.dart
│   └── shared/components/
│       ├── loading_indicator.dart
│       └── error_view.dart
│
└── main.dart

android/app/src/main/kotlin/.../
├── MainActivity.kt                # MethodChannel registration
└── ocr/
    ├── OcrMethodHandler.kt        # MethodChannel handler
    └── PaddleOcrEngine.kt         # Paddle Lite engine wrapper

ios/Runner/
├── AppDelegate.swift              # MethodChannel registration
├── OcrMethodHandler.swift         # MethodChannel handler
└── PaddleOcrEngine.swift          # Paddle Lite engine wrapper
```

## Dart API Usage

```dart
// The primary Dart API for triggering offline OCR:
Future<Either<Failure, OcrResult>> recognizeText(File image);

// Via the repository:
final repository = ref.read(ocrRepositoryProvider);
final result = await repository.recognizeText(imageFile);
result.fold(
  (failure) => print('Error: ${failure.message}'),
  (ocrResult) {
    print('Found ${ocrResult.blockCount} text blocks');
    print('Combined text: ${ocrResult.combinedText}');
    for (final block in ocrResult.textBlocks) {
      print('${block.text} (${block.confidence})');
    }
  },
);
```

## Model Configuration

Edit `lib/core/constants/app_constants.dart` to change model files, thread count, or detection thresholds:

```dart
static const String detModelFileName = 'ch_PP-OCRv3_det_slim_infer.nb';
static const String recModelFileName = 'ch_PP-OCRv3_rec_slim_infer.nb';
static const String clsModelFileName = 'ch_ppocr_mobile_v2.0_cls_slim_opt.nb';
static const int defaultThreadCount = 4;
static const int defaultMaxSideLen = 960;
```

## References

- [PaddleOCR GitHub](https://github.com/PaddlePaddle/PaddleOCR)
- [Paddle Lite](https://github.com/PaddlePaddle/Paddle-Lite)
- [Paddle-Lite-Demo (Android/iOS OCR)](https://github.com/PaddlePaddle/Paddle-Lite-Demo)
- [PP-OCRv3 Technical Report](https://arxiv.org/abs/2206.03001)
