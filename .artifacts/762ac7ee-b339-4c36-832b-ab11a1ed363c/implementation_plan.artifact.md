# Implementation Plan - Simplified Translation Update via Container Refresh

This plan implements a simplified approach to ensuring translations are always up-to-date in the reader. Instead of granular DOM updates, we will trigger a full container refresh whenever the user navigates to a new chapter in translation mode.

## Proposed Changes

### [Component] ReaderActivity

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)

1.  **Refine `onChapterEntered`**:
    *   When the detected chapter index changes (`currentSpineIndex != index`):
    *   If the book is in **Translation Mode**:
        *   Determine scroll direction: `val jumpToLast = index < currentSpineIndex`.
        *   Immediately call `loadSpineItem(index, jumpToLast)`.
        *   This will reload the WebView, clearing the container and re-fetching the current chapter and its neighbors from the disk (ensuring any recently completed translations are picked up).
        *   If `jumpToLast` is true (scrolling backward), it will land the user on the last page of the previous chapter.
        *   If `jumpToLast` is false (scrolling forward), it will land the user on the first page of the next chapter.
    *   If NOT in translation mode, continue with the existing seamless transition (just update title, position, and highlights).

## Verification Plan

### Manual Verification
1.  **Forward Update**:
    *   Open a book in translation mode.
    *   Be on Chapter 1 (Translated). Scroll down to see Chapter 2 (Original).
    *   Wait for Chapter 2 to finish translating.
    *   Scroll until Chapter 2 becomes active.
    *   **Verify**: WebView refreshes, and Chapter 2 is now Translated.
2.  **Backward Update**:
    *   Be on Chapter 2. Scroll up to see Chapter 1.
    *   Scroll until Chapter 1 becomes active.
    *   **Verify**: WebView refreshes, and we land at the end of Chapter 1.

### Automated Tests
*   N/A
