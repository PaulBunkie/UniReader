# Fixed Redundant Translation and UI Blocking

I have fixed the issues occurring on subsequent openings of a book in translation mode. The app now correctly remembers that the Table of Contents (TOC) has been translated and no longer blocks the UI with a full-screen overlay if you've already started reading.

## Changes Made

### 1. Fixed Metadata Persistence
In [LibraryProvider.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/LibraryProvider.kt), I fixed a bug in `addBook` where it was ignoring updates for books already in the library.
- **Why:** This prevented the `isTocTranslated` flag from ever being saved to disk.
- **Result:** The app now correctly remembers when the TOC translation is finished.

### 2. Optimized TOC Request
In [TranslationManager.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/TranslationManager.kt), I updated `startInitialTranslation` to re-fetch the latest metadata before deciding whether to call the server for a TOC translation.
- **Result:** If the TOC is already translated and saved to the EPUB, the server request is skipped entirely.

### 3. Smarter UI Overlay
In [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt), I refined the logic for showing the "Preparing translation..." overlay.
- **Why:** Previously, the overlay was shown by default on every open in translation mode.
- **Result:** The blocking overlay now only appears on the **very first open** of a book if the current chapter isn't ready. On subsequent opens (when you have saved progress), the overlay is hidden immediately, allowing you to resume reading while any background translation tasks continue silently.

## Verification Results

- **Build:** Success.
- **Sequential Requests:** Verified that `translate/toc` is now skipped when `isTocTranslated` is true.
- **UI:** The blocking overlay no longer appears when resuming a book you've already started.

> [!TIP]
> This fix ensures that the translation experience is seamless after the initial setup. Background translations will still show a smaller, non-blocking spinner if you scroll ahead to an untranslated chapter.
