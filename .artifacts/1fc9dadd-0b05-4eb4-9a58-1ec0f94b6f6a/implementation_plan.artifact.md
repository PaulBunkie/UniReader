# Fix Current Chapter Highlighting in Table of Contents (Improved Path Matching)

The user noticed that the current chapter highlighting in the TOC works for some books but not for others. This indicates that the current heuristic-based matching (`endsWith`, etc.) is insufficient for various EPUB directory structures.

## Proposed Changes

### Logic Improvements

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- Create a helper function `getFullPath(href: String): String` that prepends `opfDir` to the href.
- Update `updateChapterTitle()` to use these full root-relative paths for matching TOC items.
- Update `onOptionsItemSelected()` for `R.id.action_toc` to pass the full root-relative `currentHref` of the current spine item to `TOCSheet`.

#### [MODIFY] [TOCSheet.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/TOCSheet.kt)
- Update `TocAdapter` to use a more strict and reliable comparison by stripping fragments and potentially normalizing paths.
- Ensure the comparison handles cases where TOC items have fragments but the spine item doesn't.

## Verification Plan

### Manual Verification
- Test with "Standard" EPUBs (OPF in root).
- Test with "Subfolder" EPUBs (OPF in `OEBPS/`, text in `OEBPS/Text/`).
- Verify the Toolbar title is correct in both cases.
- Verify the TOC highlighting and auto-scroll work in both cases.
