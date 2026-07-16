# Implementation Plan - Fix Progress and Links in Paged Mode

The recent paging improvements caused regressions in page counting and internal link navigation. This plan fixes these by using robust coordinate detection that works correctly with CSS Columns on the `body` element.

## User Review Required

> [!IMPORTANT]
> - **Page Counting Fix**: Since columns are on the `body`, `section.scrollWidth` is not reliable. We will use `section.getClientRects().length` to accurately count pages in a chapter.
> - **Link Navigation Fix**: We will update the link/anchor scrolling logic to accurately calculate the target page index regardless of how many chapters are loaded.
> - **Padding Cleanup**: We will ensure paddings don't interfere with coordinate calculations.

## Proposed Changes

### Reader UI (WebView Logic)

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)

- **Update `updateProgress`**:
    - Use `active.getClientRects()` to get the total number of pages in the current chapter.
    - Calculate `currPage` by comparing `window.pageXOffset` with the first rect's `left` coordinate.
    - This ensures the "Page X of Y" is always accurate.

- **Update `handleInternalLink` / Anchor Sync**:
    - Improve the `sync()` function for internal links.
    - Use `target.getClientRects()[0]` or `getBoundingClientRect()` relative to the document to find the exact page.
    - Ensure `window.scrollTo` hits the exact multiple of `pw`.

- **CSS Tweak**:
    - Remove the redundant `section` padding that might be double-applying or shifting the chapter start.

## Verification Plan

### Manual Verification
- **Page Counting**: Swipe through chapters and verify "Page X of Y" updates correctly (e.g., 1/5, 2/5...).
- **Internal Links**: Click a link in the TOC and verify it jumps to the correct page in the correct chapter.
- **Anchors**: Verify that jumping to a specific footnote or chapter part works across chapter boundaries.
