# Implementation Plan - Final Precision Navigation & Snap Taming

This plan addresses the persistent issues with TOC navigation (jumping to wrong chapters) and progress detection (page numbers not updating). It focuses on isolating programmatic scrolls from the native snap mechanism and using absolute document coordinates.

## User Review Required

> [!IMPORTANT]
> - **Snapping Isolation**: We will temporarily disable `scroll-snap-type` during programmatic scrolls (TOC jumps, backward paging). This prevents the browser from "fighting" our `scrollTo` commands.
> - **Absolute Coordinate Mastery**: Instead of relying on viewport-relative `getBoundingClientRect`, we will use document-relative coordinates (`window.pageXOffset + rect.left`) for all calculations.
> - **Progress Robustness**: We will simplify chapter detection to use the element closest to the left edge, making it more resilient to rounding errors.

## Proposed Changes

### 1. Reader Activity Logic (JS & Kotlin)

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)

- **`updateSnapMarkers` (JS)**:
    - Add a `setSnapping(enabled)` helper function to toggle `scroll-snap-type` on the `html` element.
- **`scrollToChapterElement` (JS)**:
    - Call `setSnapping(false)` before scrolling.
    - Calculate `absX` using `window.pageXOffset + rect.left`.
    - Scroll to `Math.round(absX / pw) * pw`.
    - Re-enable snapping after a short delay to allow the browser to settle.
- **`prependChapter` (JS)**:
    - Disable snapping during the `scrollBy` or `scrollToLast` logic to avoid "jumpiness".
- **`updateProgress` (JS)**:
    - Improve detection logic: find the section where `rect.left` is between `-pw/2` and `pw/2`.
    - Use `Math.max(1, Math.round(active.scrollWidth / pw))` for total pages.
    - Return the actual `spineIndex` back to Kotlin for sync.

### 2. Positioning Restoration Sync
- Ensure `captureCurrentPosition` accurately reflects the chapter currently seen in the viewport before any mode switch or save.

## Verification Plan

### Manual Verification
1. **TOC**: Jump from Chapter 1 to Chapter 50. Verify it lands on Page 1.
2. **Backward**: Swipe back from Chapter 5 to Chapter 4. Verify it lands on the last page of Chapter 4.
3. **Progress**: Verify the panel updates correctly on every single page turn.
4. **Restoration**: Switch modes and verify paragraph-level persistence.
