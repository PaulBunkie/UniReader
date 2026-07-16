# Implementation Plan - Fixing Anchor and Link Precision

We will refine the anchor and link navigation logic to ensure that jumps (internal links, TOC, and position restoration) always land on the page containing the target element, rather than snapping to the nearest page boundary.

## User Review Required

> [!NOTE]
> We are distinguishing between "Snapping" (where we want the nearest page) and "Jumping" (where we want the page containing the element). This change fixes the "jumping to the next page" bug when a target is at the end of a column.

## Proposed Changes

### [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)

#### [MODIFY] `handleInternalLink()`
- Change `Math.round` to `Math.floor` for target page calculation.
- Add a 5px epsilon to ensure that elements exactly at the boundary are captured correctly.
- Formula: `var pageIndex = Math.floor((window.pageXOffset + rect.left + 5) / pw);`

#### [MODIFY] `initPagedView()`
- **`restorePosition`**: Change `Math.round` to `Math.floor` with 5px epsilon. This ensures we return to the page showing the saved line.
- **`syncAnchor`**: Change `Math.round` to `Math.floor` with 5px epsilon for cross-chapter links and TOC navigation.

#### [KEEP] `performSnap`
- Keep `Math.round` in the `performSnap` function. For manual swipes, snapping to the *nearest* boundary is the desired behavior.

## Verification Plan

### Manual Verification
1. **Footnotes at page bottom:** Click a link located near the bottom of a page. Verify that it doesn't jump to the next page.
2. **TOC Navigation:** Navigate to various chapters. Verify they start at the very first page.
3. **App Restart:** Close and reopen the app. Verify you are placed exactly on the same page you left.
4. **Drift Check:** Confirm that subpixel precision is still active and no cumulative drift appears in long chapters.
