# Implementation Plan - JavaScript Cleanup and Optimization

This plan focuses on cleaning up the accumulated "noise" (crap) in the JavaScript injected into the WebView, while preserving the core paging and alignment logic.

## Proposed Changes

### [Component] ReaderActivity (JavaScript Injection)

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)

1.  **Clean up `injectIndexingScript()`**:
    *   Remove all `console.log` and `console.warn` statements (except for critical errors).
    *   Streamline the selection menu and highlight logic (remove redundant lookups).
    *   Simplify context capture logic.
2.  **Optimize `initPagedView()`**:
    *   Remove `console.log`.
    *   Simplify the `scroll` listener. Instead of a complex loop for `active` section, use a more efficient approach (e.g., checking `window.pageXOffset`).
    *   Streamline `appendChapter` and `prependChapter`. Remove the polling `syncIdxScroll` if possible, or make it more robust/less verbose.
    *   Remove the `while (container.children.length > 3)` logic if we decide to rely more on the new container refresh strategy (though it's still useful for non-translation mode).
3.  **Optimize `initSeamlessScroll()`**:
    *   Remove `console.log`.
    *   Simplify sentinel logic.
4.  **Ensure Line Alignment**:
    *   Verify if "align by lines" refers to the horizontal snapping to `100vw`.
    *   Optionally add a CSS rule in `applyCurrentSettings` to ensure vertical alignment of lines (making page height a multiple of `line-height`) if it's missing.

## Verification Plan

### Manual Verification
1.  Verify that paging still works exactly as before (snapping to pages).
2.  Verify that highlights, fixes, and dictionary work without errors.
3.  Check Logcat to ensure the "noise" (console logs) is gone.
4.  Ensure navigation (TOC, internal links) still lands in the right place.

### Automated Tests
*   N/A
