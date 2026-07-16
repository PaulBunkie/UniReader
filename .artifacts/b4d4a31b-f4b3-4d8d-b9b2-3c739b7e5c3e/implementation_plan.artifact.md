# Implementation Plan - Precision Navigation & Positioning in Multi-Chapter Mode

Now that we load multiple chapters into a single WebView (the "простыню"), we need to update our link handling and position saving to be "section-aware".

## Proposed Changes

### 1. Robust Position Capture
- Update `captureCurrentPosition` JavaScript to return:
    - `spineIndex`: The `data-index` of the parent `<section>`.
    - `elementIdx`: The `data-idx` within that section.
    - `charOffset`: The character offset within that element.
- Update `captureCurrentPosition` signature in Kotlin to handle this triple.
- Ensure `saveReadingPosition` uses the captured `spineIndex` instead of relying solely on the activity's `currentSpineIndex` variable.

### 2. Intelligent Internal Link Handling
- Modify `handleInternalLink` to check if the target chapter is already in the DOM.
- If the chapter is already loaded:
    - Simply scroll to the anchor (or the top of that section).
    - Trigger `onChapterEntered` manually to update the UI title.
- If the chapter is NOT loaded:
    - Proceed with the full reload via `loadSpineItem`.

### 3. Navigation Polish
- Ensure `onChapterEntered` is reliable and doesn't conflict with `isJumpingToChapter`.
- Update `captureCurrentPosition` to be more accurate in detecting the top-most visible element in both paged and scroll modes.

## Verification Plan

### Manual Verification
- **Link Reliability**: Tap a link in the TOC that points to a nearby chapter (already loaded). Verify it scrolls without reloading.
- **Distant Links**: Tap a link to a far-away chapter. Verify it reloads correctly and positions you at the right spot.
- **Position Saving**: Read until you are in the middle of a seamless transition (e.g., page 1 of the NEXT chapter). Close and reopen the app. Verify you are exactly where you left off.
- **Backwards Links**: Tap a "Back to top" link or similar within the seamless document. Verify correct positioning.
