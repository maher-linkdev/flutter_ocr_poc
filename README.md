# flutter_ocr_poc

A new Flutter project.

## Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Lab: Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Cookbook: Useful Flutter samples](https://docs.flutter.dev/cookbook)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.


### Current state

Current OCR pipeline per bounding box:
1. Detect all text regions in the image (DB algorithm)
2. Crop each region from the original image (axis-aligned rectangle, expanded
   by unclip ratio 1.8)
3. Classify rotation (0° vs 180°) and rotate if needed
4. Preprocess (bilateral filter → sharpen → brightness normalize → super-res
   if small)
5. CLAHE contrast enhancement
6. Recognize text (ONNX model → CTC decode)
7. Reverse entire string if it contains any Arabic character

What's going wrong:

┌────────────────┬────────────────────────────────────────────────────────┐
│    Problem     │                       Root Cause                       │
├────────────────┼────────────────────────────────────────────────────────┤
│ ID number /    │ containsArabic() checks U+0600-U+06FF which includes   │
│ birthday       │ Arabic-Indic digits (٠١٢٣٤٥٦٧٨٩). A bbox with only     │
│ reversed       │ digits triggers full reversal, but digits are LTR —    │
│                │ they should NOT be reversed                            │
├────────────────┼────────────────────────────────────────────────────────┤
│ Last name      │ The bbox crops include excess background from the 1.8x │
│ incorrect      │  unclip expansion. Depending on where the name sits on │
│ (first name    │  the card, one crop may have more noise/border         │
│ OK)            │ artifacts than the other, hurting recognition          │
└────────────────┴────────────────────────────────────────────────────────┘

  ---
What we'll refactor

1. Fix reversal logic (containsArabic → smarter bidi handling)

Before: Reverse the entire string if ANY character is in Arabic unicode range
(includes digits)

After:
- Exclude Arabic-Indic digits (U+0660-U+0669, U+06F0-U+06F9) from the Arabic
  letter check
- For mixed text (letters + digits in same bbox): reverse only the Arabic
  letter runs, keep digit sequences in their original LTR order

This fixes ID numbers and birthdays while keeping Arabic names correctly
reversed.

2. Add bbox crop trimming (new trimCropWhitespace step)

Before: Crop goes directly from detection → classification → recognition (with
padded background)

After: Add a trimming step between crop and classification:
- Convert to grayscale → Otsu threshold to find text vs background
- Find tight bounding box of actual text pixels (row/column projection)
- Trim to that tight box with small padding (2-4px)
- This removes background noise that can confuse the recognition model

Pipeline becomes: Detect → Crop → Trim → Classify → Preprocess → CLAHE →
Recognize

