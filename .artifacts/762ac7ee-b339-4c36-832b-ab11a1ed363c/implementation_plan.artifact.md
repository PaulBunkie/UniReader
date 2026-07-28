# Implementation Plan - Restoring Seamless Scroll

This plan restores the original, truly seamless behavior for the vertical scroll mode while maintaining the translation-aware reload logic for paged mode.

## Proposed Changes

### [Component] ReaderActivity Logic

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)

1.  **Refine `onChapterEntered`**:
    *   Add a check for `isPagedMode`.
    *   If `isPagedMode && currentBookMetadata?.isTranslationMode == true`, keep the 500ms delayed reload logic (to ensure translations are picked up in horizontal view).
    *   If `!isPagedMode` (Seamless mode), **restore the original logic**:
        *   Immediately update `currentSpineIndex`.
        *   Update chapter title.
        *   Notify `translationManager`.
        *   Apply highlights via JS.
        *   **Do NOT** trigger a container reload (`loadSpineItem`). This preserves the continuous vertical scroll feeling.
2.  **Fix `initSeamlessScroll`**:
    *   Correct the base URL in `webView.loadDataWithBaseURL` (ensure it uses `epub://reader/` or consistent naming).
    *   Verify that the 3-chapter sliding window logic (`loadAndAppend/Prepend`) is correctly initialized.

## Verification Plan

### Manual Verification
1.  **Seamless Mode**:
    *   Open a book and switch to vertical scroll (seamless).
    *   Scroll across chapter boundaries.
    *   **Verify**: The scroll remains perfectly continuous. No flashes or reloads occur when crossing from Chapter 1 to Chapter 2.
2.  **Paged Mode**:
    *   Switch to horizontal paging.
    *   Swipe to a new chapter.
    *   **Verify**: The 500ms delayed reload still happens to ensure the new chapter is translated.
3.  **Consistency**:
    *   Ensure TOC jumps still work in both modes.
