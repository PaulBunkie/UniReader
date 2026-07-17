# Implementation Plan - Precision Snap & Layout Stability

Integrate native CSS Scroll Snap and fix Table of Contents (TOC) inaccuracies by implementing a layout stability check ("smart micro-pause").

## User Review Required

> [!IMPORTANT]
> - **Layout Stability**: We will no longer jump as soon as `scrollWidth > width`. Instead, we wait for `scrollWidth` to stop changing. This fixes the "TOC lands on wrong page" bug.
> - **Snap Integration**: Snapping will be hardware-accelerated via CSS. Programmatic scrolls (TOC, Prev/Next buttons) will temporarily disable snapping to prevent "vibration" or "bouncing".
> - **Zero Layout Impact**: All snap markers will be `position: fixed`, ensuring they don't affect your existing CSS column calculations.

## Proposed Changes

### 1. CSS Refinement
#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- Add `scroll-snap-type: x mandatory` to `html`.
- Add styles for `#snap-ribbon` and `.snap-point` (fixed positioning, zero height).

### 2. Smart Layout "Micro-pause"
#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- Update `appendChapter` / `syncIdxScroll`:
    - Track `lastWidth`.
    - Only execute `window.scrollTo` when `currentWidth == lastWidth` (layout has settled).
    - Disable snap during this process.

### 3. Snapping Toggle Helper
#### [NEW] JavaScript Helper
- Implement `setSnapping(enabled)` to toggle the CSS property.
- Implement `updateSnapMarkers()` to keep the "magnet grid" in sync with document length.

### 4. Removal of Manual Magnet
#### [DELETE] Old JS Logic
- Remove `performSnap()` and the legacy scroll event listener from commit `38330c1`.

## Verification Plan

### Automated Tests
- Build and run the app.

### Manual Verification
- **TOC Precision**: Click a chapter in the middle of a large book. Verify it lands exactly on Page 1.
- **Link Accuracy**: Click an internal `epub://` link. Verify it lands on the correct paragraph.
- **Swipe Feel**: Verify horizontal paging is smooth and "sticks" to edges without JS lag.
- **Backward Paging**: Scroll to the top to load the previous chapter. Verify no jumping occurs.
