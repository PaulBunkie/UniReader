# Implementation Plan: Save Improved EPUB to a New File

This plan addresses the `SecurityException` by implementing a "Save As" flow. Instead of overwriting the original source, the app will create a new EPUB file containing all text improvements.

## Proposed Changes

### [Reader UI - Menu Refactoring]

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- Update the `PopupMenu` for the Settings icon to use English labels:
    - **Appearance**: Opens the settings sheet.
    - **Save Improved Copy**: Triggers the file creation flow.
- Implement a `registerForActivityResult` with `ActivityResultContracts.CreateDocument("application/epub+zip")`.
- When the user selects a destination, launch `EpubModifier`.

### [EPUB Modification Logic]

#### [MODIFY] [EpubModifier.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/EpubModifier.kt)
- Update `applyFixes` to accept `sourceUri` and `destinationUri`.
- Ensure it streams content from the source ZIP to the destination ZIP, applying Jsoup modifications only to the files with pending fixes.
- Maintain EPUB validity (uncompressed `mimetype` first).

### [Database Layer]

#### [MODIFY] [HighlightDatabase.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/HighlightDatabase.kt)
- No changes needed (already has `getPendingFixes`).

## Verification Plan

### Manual Verification
1. Make a few green fixes in the book.
2. Select **Settings (⚙️)** -> **Save Improved Copy**.
3. Choose a destination (e.g., Downloads folder) and a new name (e.g., `book_fixed.epub`).
4. Wait for the progress dialog to finish.
5. Locate the new file and open it.
6. Verify that the text improvements are now part of the permanent text of the new book.
7. Verify the original book remains unchanged.
