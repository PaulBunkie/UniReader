# Implementation Plan - Seamless Paging (Stability First)

The goal is to implement seamless transitions between chapters in paged mode without breaking the layout or the existing navigation stability.

## User Review Required

> [!IMPORTANT]
> I am moving to a **unified chapter management** system. Instead of reloading the WebView in paged mode, it will behave like the seamless scroll mode but with horizontal pagination CSS.
> I will use the exact CSS that is currently working for you, adding only the necessary rules for chapter separation.

## Proposed Changes

### 1. ReaderActivity.kt - Logic Updates
- **Unified Loading**: `onReachedBottom` and `onReachedTop` will now trigger for both modes.
- **CSS Migration**: Move the `column-width` and paging rules to a more flexible structure that supports multiple `section` tags.
- **Initialization**: `initPagedView` will now initialize an empty container and load the current chapter + neighbors, mirroring the stable seamless scroll logic.

### 2. CSS Updates (in `applyCurrentSettings`)
- Add `break-before: column` to `section` elements in paged mode. This ensures each chapter starts on a fresh page.
- Ensure `body` remains the primary column container for maximum stability.

### 3. JavaScript Interface Updates
- **`appendChapter` / `prependChapter`**: Update these to handle horizontal scroll adjustments. When a chapter is added to the beginning, we must shift `scrollLeft` so your current page doesn't "jump".
- **`nextPage` / `prevPage`**: These remain almost the same, but they will now naturally flow into the next chapter because it's already in the same document.

## Verification Plan

### Manual Verification
- **Page Flipping**: Flip through a whole chapter. Verify the next chapter appears as the next page without any loading screen.
- **Backwards Navigation**: Flip back from page 1 of Chapter 2. Verify Chapter 1 is there.
- **Stability**: Ensure no text overlapping ("каша") occurs by sticking to standard block-level column splitting.
- **Toolbar**: Ensure the chapter title updates when you cross the boundary.
