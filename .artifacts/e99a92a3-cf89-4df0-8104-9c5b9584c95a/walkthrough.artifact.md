# Walkthrough: Text Highlighting and Custom Context Menu

Implemented a system for highlighting text in the reader with persistence in an external SQLite database and a custom context menu item.

## Changes Made

### 1. Data Model & Storage
- Created [HighlightModel.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/HighlightModel.kt) to represent a text highlight.
- Implemented [HighlightDatabase.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/HighlightDatabase.kt) using `SQLiteOpenHelper` to store highlights by `bookUri` and `spineIndex`.

### 2. Custom Context Menu
- Added a "Выделить" item to the `WebView` text selection menu in [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt).
- Used `onPrepareActionMode` with order `0` to ensure it appears first.
- Implemented a fallback mechanism using reflection to bypass a compiler issue with `setCustomSelectionActionModeCallback`.

### 3. JavaScript Bridge & Logic
- Added `getSelectionDetails()` to the indexing script to capture `elementIdx` (paragraph index), `startOffset`, and `endOffset`.
- Implemented `applyHighlights(json)` in JS to wrap highlighted text in `<mark class="uni-highlight">` tags.
- Added a `saveHighlight` bridge method in Kotlin to receive and persist data from JS.

### 4. UI & Theming
- Added CSS for `mark.uni-highlight` in both Light and Dark modes:
    - **Light:** Yellow background (`#ffeb3b`) with black text.
    - **Dark:** Amber/Orange background (`#f57f17`) with white text for better contrast.
- Highlights are automatically applied when:
    - A chapter is first loaded.
    - A new chapter is appended or prepended in seamless scroll mode.
    - The user swipes between chapters (refreshed in `onChapterEntered`).

## Verification Results

### Manual Verification
1.  **Selection:** Selecting text in the reader now shows the "Выделить" item at the top of the context menu.
2.  **Highlighting:** Clicking "Выделить" immediately highlights the text with the theme-appropriate color.
3.  **Persistence:** Highlights are saved to the SQLite database and remain visible after restarting the activity or swiping away and back to the chapter.
4.  **Theme Support:** Switched between Light and Dark modes; highlights remained readable and clearly visible.
