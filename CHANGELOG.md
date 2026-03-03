# Changelog

## [Unreleased] - 2026-03-03

### Fixed
- **Arabic-Indic digit reversal** — ID numbers and birthdays containing Arabic-Indic digits (٠-٩, U+0660–U+0669 / U+06F0–U+06F9) were incorrectly reversed. These digits fall within the Arabic Unicode block, causing `containsArabic()` to trigger full string reversal. Now digits are excluded from Arabic letter detection.
- **Mixed Arabic+digit text reversal** — Replaced naive `text.reversed()` with smart run-based reversal (`smartArabicReverse()`). Splits text into Arabic letter runs and non-Arabic runs (digits, spaces, punctuation), reverses Arabic runs and overall run order, but preserves digit sequence order. Example: CTC output "٥٤٣٢١ مقر" now correctly produces "رقم ٥٤٣٢١".

### Added
- **Edge trimming for cropped text regions** — New `trimCropBackground()` method trims excess background caused by detection unclip expansion (ratio 1.8). Uses grayscale luminance, Otsu thresholding, and row/column projection to find tight content bounds with 3px padding. Integrated into pipeline between crop and classify steps.
