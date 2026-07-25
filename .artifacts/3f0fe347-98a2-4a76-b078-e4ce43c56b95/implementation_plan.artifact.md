# Implementation Plan: Save Text Improvements with Visual Feedback

This plan implements saving text improvements to the local database and displaying them in the reader with a distinct highlight and an interactive overlay.

## User Review Required

> [!NOTE]
> **Visual Styling:**
> - Improvements will be highlighted in **Light Green** (instead of the standard yellow for highlights).
> - Tapping a green highlight will show a small overlay with the improved version of the text.
> - The original text remains in the document, but it is visually marked.

## Proposed Changes

### [Database & Model]
- **`Highlight.kt`**: Ensure the `replacementText` property is correctly serialized/deserialized.
- **`HighlightDatabase.kt`**: Already supports `replacement_text`, no changes needed.

### [Reader Logic - ReaderActivity.kt]

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- **Save Action**: Implement `acceptImprovement()`:
    - Trigger a JS call `getSelectionDetails(true, improvedText)` to get the selection offsets and original text, then call back to `saveHighlight`.
- **Bridge Updates**:
    - Update `AndroidReader.saveHighlight(json)` to parse `replacementText` and save it to the DB.
- **UI Updates**:
    - Update `getHighlightsJson()` to include the `replacementText` field for each highlight.
    - Update CSS in `applyCurrentSettings()` to include styles for `.uni-fix` (the green highlight).
    - Update `injectIndexingScript()`:
        - **JS `applyHighlights`**: If `replacementText` is present, use class `.uni-fix` instead of `.uni-highlight`.
        - **JS Interaction**: Add a listener to show a "bubble" tooltip when a `.uni-fix` element is tapped. The tooltip will display the improved text.
        - **JS `getSelectionDetails`**: Update to support passing `replacementText`.

## Verification Plan

### Manual Verification
1. Select text, click "Исправить", wait for result.
2. Click "Сохранить".
3. Verify the selection turns **Green**.
4. Tap the green area and verify a small overlay appears showing the improved text.
5. Close and reopen the book to verify the green highlight persists.
