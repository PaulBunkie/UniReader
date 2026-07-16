# Implementation Plan - Native CSS Scroll Snap with Simulated Paging Gap

This plan integrates native CSS Scroll Snap into the WebView reader while strictly preserving the existing "padding-based" column gap logic. It also fixes the progress panel lag by making detection autonomous in JavaScript.

## User Review Required

> [!IMPORTANT]
> - **Zero Layout Changes**: We will keep `column-gap: 0` and use the existing `padding` on elements to simulate the gap. This ensures the document width remains a clean multiple of `100vw`.
> - **Native Snapping**: The manual JavaScript "magnet" logic will be replaced with `scroll-snap-type: x mandatory`.
> - **Autonomous Progress**: The progress panel will now be driven by JS detecting the visible chapter, eliminating the lag when crossing section boundaries.

## Proposed Changes

### Reader UI (WebView Styling & Logic)

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)

- **Update `applyCurrentSettings`**:
    - Add `scroll-snap-type: x mandatory;` and `overflow-x: auto;` to the `html` tag.
    - Add styles for the invisible `#snap-ribbon` and `.snap-point` markers.
    - Preserve the `column-gap: 0` and `padding` logic for text elements.
- **Update `initPagedView`**:
    - Add `<div id="snap-ribbon"></div>` to the body.
    - Implement `updateSnapMarkers()` in JavaScript to populate the ribbon based on `scrollWidth / clientWidth`.
    - Trigger `updateSnapMarkers()` in `appendChapter`, `prependChapter`, and on `resize`.
    - **Remove** the old manual `performSnap()` and its associated scroll listener logic.
- **Update `updateProgress`**:
    - Modify the injected JS to autonomously find the active `<section>` by checking `getBoundingClientRect().left`.
    - Return the actual section index (`data-index`) to Kotlin.
    - Update `currentSpineIndex` and the UI based on the detected index.

## Verification Plan

### Manual Verification
- Verify horizontal paging feels smooth and "snaps" perfectly to page boundaries.
- Verify that changing font size or screen orientation updates the snapping points correctly.
- Verify the bottom progress panel ("Section X/Y") updates immediately as soon as a new chapter enters the view.
- Verify no visual changes to text paddings or margins.
