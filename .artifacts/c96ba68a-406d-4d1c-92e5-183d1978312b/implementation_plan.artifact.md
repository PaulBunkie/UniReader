# Fix Redundant Translation and Stuck Overlay

The user is experiencing two main issues on the second opening of a book in translation mode:
1. The app makes a redundant request to translate the TOC.
2. The "Preparing translation..." overlay is shown even when it shouldn't be.

## Analysis

1.  **Redundant TOC Request**: `LibraryProvider.addBook` has a bug where it refuses to update an existing entry. When `TranslationManager` finishes the TOC translation and tries to save `isTocTranslated = true` via `addBook`, the change is ignored. Consequently, every time the book is opened, the app thinks the TOC is untranslated and hits the server.
2.  **Stuck Overlay**: `ReaderActivity` sets `initialTranslationOverlay` to `VISIBLE` by default in translation mode. While there is a "warm start" check to hide it, it doesn't account for whether this is a subsequent opening (where we have saved progress).

## Proposed Changes

### [Library Persistence]

#### [MODIFY] [LibraryProvider.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/LibraryProvider.kt)
- Fix `addBook` to update the existing `BookMetadata` entry if the URI matches, instead of returning early. This ensures `isTocTranslated` and `translationGuidelines` are persisted.

### [UI Logic]

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- Refine the overlay logic in `onCreate`:
    - Only show `initialTranslationOverlay` if it's the very first time the book is opened (no saved progress: `lastSpineIndex == 0` and `lastElementIndex == -1`) **AND** the starting chapter is not yet translated.
    - If it's a subsequent opening, hide the initial overlay immediately and rely on the background `processingOverlay` for any pending translations.

### [Translation Workflow]

#### [MODIFY] [TranslationManager.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/TranslationManager.kt)
- Ensure metadata is re-fetched before checking `isTocTranslated` in `startInitialTranslation` to avoid using stale state from the constructor.

## Verification Plan

### Manual Verification
1.  Open a new book in translation mode. Let the TOC and first chapter translate.
2.  Close the book.
3.  Open the same book again.
4.  **Expectation**: The TOC request (`translate/toc`) is **not** sent to the server. The full-screen overlay does **not** appear (or disappears instantly).
