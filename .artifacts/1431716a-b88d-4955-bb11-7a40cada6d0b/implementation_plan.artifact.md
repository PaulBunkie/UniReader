# Highlight Current Chapter in Table of Contents

This plan describes how to highlight the currently reading chapter in the table of contents (TOC) sheet.

## User Review Required

> [!NOTE]
> The matching between the current reading position and the TOC entry will be based on the current spine item's `href`. If multiple TOC entries point to the same file, the first one matching will be highlighted, as the app currently tracks progress primarily by spine index.

## Proposed Changes

### [app]

#### [MODIFY] [TOCSheet.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/TOCSheet.kt)
- Update `TOCSheet` constructor to accept `currentHref: String?`.
- Update `TocAdapter` to accept `currentHref` and determine the `selectedIndex`.
- In `TocAdapter.onBindViewHolder`, apply a different style (primary color and bold text) for the selected item.
- In `TOCSheet.onViewCreated`, scroll the `RecyclerView` to the `selectedIndex` if it's valid.

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- When showing `TOCSheet` in `onOptionsItemSelected`, pass the `href` of the current spine item: `book.spine[currentSpineIndex].href`.

## Verification Plan

### Manual Verification
1. Open a book.
2. Go to a specific chapter.
3. Open the Table of Contents.
4. Verify that the current chapter is highlighted (e.g., has a different color or bold text).
5. Verify that the TOC automatically scrolls to the highlighted chapter if it's not visible.
6. Navigate to another chapter from the TOC and verify it works as expected.
