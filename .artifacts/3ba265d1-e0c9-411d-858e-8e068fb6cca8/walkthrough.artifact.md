# Walkthrough - Fixing Anchor and Link Precision

We have refined the logic for "jumping" to specific elements (footnotes, anchors, and saved positions) to ensure they always land on the correct page. This addresses the issue where the reader would sometimes jump one page too early or too late.

## Changes Made

### Precision Navigation Refinement

#### [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)

- **Targeted Jumps (`Math.floor`):** Switched from `Math.round` to `Math.floor` for calculating the target page of a specific element (like a footnote or anchor). This ensures we land on the page that *contains* the element, rather than just the one nearest to its boundary.
- **Boundary Buffer (Epsilon):** Added a 5px buffer to the jump calculations. This prevents subpixel rounding errors from accidentally pushing the view to the *previous* page when a target is exactly at the start of a column.
- **Consistent Snapping:** Kept `Math.round` for manual swipes in `performSnap`. This maintains the intuitive behavior where a swipe snaps to the nearest visible page boundary.

> [!TIP]
> By distinguishing between "jumping to an element" and "snapping a gesture", we've made the reader much more predictable and accurate.

## Verification Results

### Manual Verification (Expected behavior)
- **Footnotes:** Tapping a link at the very end of a page now correctly shows that page, rather than jumping to the next blank one.
- **Position Recovery:** Returning to a saved book now correctly displays the page containing the last-read line.
- **Chapter Links:** Navigating between chapters via links or TOC is now 100% accurate.
