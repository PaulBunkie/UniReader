# Task List: Save Text Improvements

- [x] Update `ReaderActivity.kt`:
    - [x] Update `getHighlightsJson` to include `replacementText`
    - [x] Update `applyCurrentSettings` with `.uni-fix` CSS
    - [x] Update `injectIndexingScript`:
        - [x] Update `getSelectionDetails` JS function
        - [x] Update `applyHighlights` JS function to handle replacements
        - [x] Add JS logic for showing/hiding improvement tooltips
    - [x] Update Android-JS bridge (`saveHighlight`)
    - [x] Implement `acceptImprovement()` logic
    - [x] Bind `btnFixAccept` click listener
- [x] Verify functionality
