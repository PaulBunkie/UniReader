# Implementation Plan - Removing Highlights

Currently, the app allows users to select text and save it as a highlight. This plan outlines the changes needed to allow users to remove these highlights.

## Proposed Changes

### [HighlightDatabase](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/HighlightDatabase.kt)
- [MODIFY] Add `deleteHighlight(id: Long)` method to remove a highlight from the `highlights` table by its ID.

### [ReaderActivity](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- [MODIFY] Update `getHighlightsJson(spineIndex: Int)` to include the `id` of each highlight.
- [MODIFY] Update `setupWebView()` (JavascriptInterface):
    - Add `deleteHighlight(id: String)` method.
- [MODIFY] Update `injectIndexingScript()`:
    - Update `selectionchange` listener:
        - Detect if the current selection is contained within a `.uni-highlight` element.
        - If contained: Change `#uni-highlight-btn` text to "Удалить выделение" and set its action to delete.
        - If not contained: Keep text as "Сохранить выделение" and set its action to save.
    - Important: **No new click listeners** on the document or marks to preserve reader gestures (paging/UI toggle).

## Verification Plan

### Manual Verification
1. Open a book in the reader.
2. Select some text and click "Сохранить выделение".
3. Verify the text is highlighted.
4. Click on the highlighted text.
5. A "Удалить выделение" button (or similar) should appear.
6. Click the button.
7. Verify the highlight is removed from the screen and does not reappear when reloading the chapter.
