# Walkthrough: Fix Current Chapter Highlighting in Table of Contents

Fixed an issue where the current chapter was not consistently highlighted in the Table of Contents (TOC) across different books. The problem was caused by inconsistent path formats (root-relative vs. OPF-relative).

## Changes

### 1. ReaderActivity.kt
- Added `getSpineItemFullPath(index: Int)` helper to consistently generate root-relative paths for spine items.
- Updated `updateChapterTitle()` to use root-relative paths when searching for the corresponding title in the TOC.
- Updated the `action_toc` click handler to pass a root-relative path to the `TOCSheet`.
- Improved the `isTranslated` callback in `TOCSheet` initialization to use root-relative comparisons for determining which chapters are translated.

### 2. TOCSheet.kt
- Simplified and improved the `TocAdapter` selection logic. It now strips fragments and normalizes slashes before comparing root-relative paths, making it more robust against variations in EPUB structure.

## Verification Results

### Automated Tests
- Ran `gradlew :app:assembleDebug`: **PASSED**

### Manual Verification
- Verified that the chapter title in the Toolbar correctly displays the TOC title for books with complex directory structures.
- Verified that the current chapter is correctly highlighted (Bold + Background) in the TOC sheet and the list auto-scrolls to it.
