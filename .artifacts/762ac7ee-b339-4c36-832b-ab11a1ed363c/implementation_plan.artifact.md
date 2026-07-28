# Implementation Plan - Harmonizing Margins Between Paged and Seamless Modes

This plan addresses the inconsistency in margins and paddings between the horizontal paged mode and the vertical seamless scroll mode.

## Proposed Changes

### [Component] ReaderActivity Logic

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)

1.  **Unify Element Styles in `applyCurrentSettings()`**:
    *   Merge the rules for `p, div, h1, h2, h3, h4, h5, h6, li`.
    *   Ensure `div` is included in both modes' horizontal padding rules.
    *   Use a consistent method for horizontal spacing (using `halfGapPx` as padding-left and padding-right).
    *   For vertical spacing, keep using `padding-bottom` in Paged mode (to avoid column break issues) and `margin-bottom` in Seamless mode, but ensure they use the same formula: `${settings.paragraphSpacing * lh}em`.
2.  **Ensure Consistent CSS Refresh**:
    *   Update `updateWebViewPadding()` to call `applyCurrentSettings()` regardless of the mode. This ensures that changes to padding or column gap sliders always update the internal CSS as well as the container's native padding.
3.  **Refine `commonCss`**:
    *   Move the text-indent and other shared paragraph properties to a shared block that is explicitly overridden by mode-specific rules only where necessary.

## Verification Plan

### Manual Verification
1.  **Horizontal/Vertical Consistency**:
    *   Switch between Paged and Seamless modes.
    *   **Verify**: The left and right margins of the text should stay exactly the same.
    *   **Verify**: The vertical spacing between paragraphs should remain consistent.
2.  **Div Handling**:
    *   Open a book that uses `<div>` tags for paragraphs.
    *   **Verify**: Dividers and blocks have the same horizontal margins as standard `<p>` paragraphs in both modes.
3.  **Slider Updates**:
    *   Adjust the "Column Gap" and "Left/Right Margin" sliders.
    *   **Verify**: The changes are reflected immediately and correctly in both reading modes.
