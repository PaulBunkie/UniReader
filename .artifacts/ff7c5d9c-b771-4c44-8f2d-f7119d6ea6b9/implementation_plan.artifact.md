# Implementation Plan - Metadata Fix & Progress Indicator

This plan addresses the inconsistent metadata display and adds a reading progress indicator (chapter count) to the library list.

## Research Findings

### 1. Metadata Discrepancies
- **Cause**: The `EpubParser` currently looks strictly for `dc:title` and `dc:creator`. If a book uses namespaced tags or simplified tags, it might fail. The "EPUB Translator Tool" author is likely embedded in the specific EPUB file metadata by a conversion tool.
- **Fix**:
    - Improve `EpubParser` to handle tags with and without `dc:` prefix.
    - Implement a fallback for titles using the filename from the URI if the metadata title is missing or generic (like "Unknown Title").
    - Use "Unknown Author" consistently if metadata is missing.

### 2. Progress Indicator
- **Data**: We need to store the total number of chapters (spine items) in the library.
- **UI**: Display progress as "Current Chapter / Total Chapters" (e.g., `5/18`) in the book list item.

## Proposed Changes

### Data Layer

#### [MODIFY] [BookMetadata.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/BookMetadata.kt)
- Add `val totalSpineItems: Int = 0` field.

#### [MODIFY] [LibraryProvider.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/LibraryProvider.kt)
- Update JSON serialization/deserialization to include `totalSpineItems`.

### Parser & Logic

#### [MODIFY] [EpubParser.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/EpubParser.kt)
- Update `parseOpf` to check for both `dc:title`/`title` and `dc:creator`/`creator`/`dc:author`/`author`.
- Ensure title and author are trimmed.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/MainActivity.kt)
- When adding a new book to the library, set `totalSpineItems = epubBook.spine.size`.
- Improve title fallback: if `epubBook.title` is null or blank, extract the filename from the URI.

### UI Layer

#### [MODIFY] [item_book.xml](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/res/layout/item_book.xml)
- Add a new `TextView` (`textProgress`) in the top right or bottom right corner.
- Set its appearance to be subtle (small text, slightly transparent).

#### [MODIFY] [BooksAdapter.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/BooksAdapter.kt)
- In `onBindViewHolder`, calculate and set the progress text: `"${book.lastSpineIndex + 1}/${book.totalSpineItems}"`.
- Handle the case where `totalSpineItems` is 0 (for existing books) by showing `-/-` or nothing.

## Verification Plan

### Manual Verification
1.  **Add a new book**:
    - Verify that the title and author are extracted more reliably.
    - Verify that if metadata is missing, the filename is used as the title.
2.  **Check Progress**:
    - Verify that a book shows `1/18` (or similar) in the library list.
3.  **Read and Return**:
    - Open a book, move to chapter 5, then go back to the library.
    - Verify that the progress updates to `5/18`.
