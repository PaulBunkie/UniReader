# Implementation Plan - Native Scroll Snap (Minimalist Integration)

This plan adds native CSS Scroll Snapping to the reader while strictly preserving all existing navigation and layout logic from the stable baseline.

## User Review Required

> [!IMPORTANT]
> - **Selective Snapping**: Snapping will be enabled on the `html` element. To prevent it from interfering with TOC jumps or mode switching, we will add a `setSnapping(enabled)` helper to toggle it during programmatic scrolls.
> - **No Layout Shifts**: We will keep `column-gap: 0` and the current padding-based gap simulation. The snap points will be exactly `100vw` wide.
> - **Removal of Manual Magnet**: The JS-based `performSnap` logic will be removed as it's no longer needed with native snapping.

## Proposed Changes

### 1. Update CSS Styles (applyCurrentSettings)
#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- Add `scroll-snap-type: x mandatory` to the `html` tag in paged mode.
- Add styles for the invisible `#snap-ribbon` and `.snap-point` markers.

### 2. Update HTML & JavaScript (initPagedView)
#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- Add `<div id="snap-ribbon"></div>` to the body.
- Implement `setSnapping(enabled)` to toggle the `scrollSnapType` CSS property.
- Implement `updateSnapMarkers()` to fill the ribbon based on `scrollWidth / clientWidth`.
- Call `updateSnapMarkers()` on window `resize` and inside `appendChapter`/`prependChapter`.
- **Delete** the old manual `performSnap` function and its scroll listener.

### 3. Integrate Snapping Toggles
#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- In `nextPage()`, `prevPage()`, `syncIdxScroll()`, and `handleInternalLink` (the `sync` part), wrap the `window.scrollTo` calls with `setSnapping(false)` and `setSnapping(true)` to ensure programmatic jumps are precise and not "bounced back" by the snapping engine.

## Verification Plan

### Manual Verification
- Swipe left/right: Verify the page "sticks" perfectly to the 100vw boundaries.
- TOC Jump: Click a chapter in the TOC. Verify it lands on Page 1 without any visual "fighting".
- Rotation: Rotate the screen and verify snapping still works for the new width.
- Mode Toggle: Switch Scroll ↔ Paged and verify your position is preserved.
