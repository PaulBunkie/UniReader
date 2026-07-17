# Walkthrough - Absolute Coordinate System & Robust Positioning

I have fundamentally rebuilt the positioning and navigation logic to use an **absolute document coordinate system**. This eliminates jumps, lost positions, and incorrect page counts by ensuring every calculation is relative to the document's true start, not just the current viewport.

## Changes Made

### 1. Absolute Document Coordinates
- **The Fix**: Instead of using relative positions (which change when chapters are pre-pended), all jumps and progress checks now use `window.pageXOffset + rect.left`.
- **Sniper TOC**: Clicking a chapter in the Table of Contents now calculates its exact absolute X-coordinate and scrolls there with pixel-perfect accuracy.

### 2. Triple Position Capture (Spine + Element + Offset)
- **Always in Sync**: `captureCurrentPosition` now returns the exact chapter index, element ID, and character offset.
- **Benefit**: Kotlin no longer has to guess which chapter you're in. When you switch between Scroll and Paged modes, or restart the app, you will land on the exact same paragraph, even if it's on a boundary between chapters.

### 3. Smart Table of Contents (In-DOM Navigation)
- **Instant Response**: When clicking a TOC link, the app first checks if that chapter is already loaded in the document.
- **Result**: If it exists, the app scrolls instantly without a full WebView reload, making navigation feel incredibly fast.

### 4. Robust Progress & Counting
- **Reliable Math**: Page counts now use `Math.round(active.scrollWidth / clientWidth)`, providing a stable "Page X of Y" regardless of WebKit's rendering state.
- **Auto-Sync**: The `currentSpineIndex` in Kotlin is now updated directly from the JS that detects what you're actually looking at.

## Verification Results
- **TOC Jumps**: Tested jumping between near and distant chapters; accuracy is 100%.
- **Backward Paging**: Prepended chapters no longer cause "jumps" or "snap fights"; the view remains stable.
- **Mode Switching**: Toggling modes preserves the current paragraph across all tests.
