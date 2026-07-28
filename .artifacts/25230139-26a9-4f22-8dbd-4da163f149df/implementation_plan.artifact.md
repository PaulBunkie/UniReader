# Implementation Plan - TOC Visuals and Matching Fixes

Improve TOC legibility in Light Mode and fix the matching logic that prevents some translated chapters from being highlighted.

## User Review Required

> [!NOTE]
> I will pick a darker, more saturated orange for Light Mode to ensure it stands out against the white background while remaining consistent with the "yellow" theme of translated content.

## Proposed Changes

### [UI Components] TOCSheet.kt
#### [MODIFY] [TOCSheet.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/TOCSheet.kt)
- Update `onBindViewHolder` to handle theme-dependent coloring.
- Detect theme mode using `context.resources.configuration.uiMode`.
- **Light Mode**: Use `#E65100` (Dark Orange).
- **Dark Mode**: Use `#FBC02D` (Bright Yellow).

### [Reader Logic] ReaderActivity.kt
#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- Improve the `isTranslated` matching logic in the `action_toc` handler:
    - Strip fragments (anything after `#`) from the TOC `href` before searching the spine.
    - Ensure robust matching even with different path prefixes.

## Verification Plan

### Manual Verification
1.  **Light Mode Test**: Switch to Light Mode, open TOC. Verify translated chapters use a darker, legible orange.
2.  **Matching Test**: Verify that chapters with anchors in TOC (like `part0005.xhtml#anchor`) are now correctly identified as translated if their corresponding spine item is ready.
3.  **Cross-Check**: Compare TOC highlights with the actual translation status of chapters.
