# Implementation Plan: Context and Hotpoints for Text Improvement

This plan details how to implement the expanded `/api/improve` protocol by automatically gathering surrounding context and existing highlights (hotpoints) from the WebView.

## User Review Required

> [!NOTE]
> **Automation Details:**
> - **Context**: When you click "Исправить", the app will automatically grab the text of the paragraph containing the selection, plus one paragraph before and one after.
> - **Hotpoints**: Any existing highlights (yellow marks) that fall within your current selection will be automatically detected and sent to the API as "hotpoints".
>
> This requires no changes to your current workflow—it just makes the "Improve" button smarter.

## Proposed Changes

### [ReaderActivity]

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- Update `fixText(text: String)` to `fixText(json: String)`.
- Parse the JSON incoming from JavaScript which will contain `text`, `context`, and `hotpoints`.
- Pass these fields to `FixService`.

#### [MODIFY] [injectIndexingScript() in ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- Update the "Fix" button click listener in JS:
    - Use `window.getSelection().getRangeAt(0)` to analyze the selection.
    - Identify the container element (usually a `<p>`).
    - Collect text from `previousElementSibling`, `this`, and `nextElementSibling` for `context`.
    - Use `querySelectorAll('.uni-highlight')` within the selection range to find `hotpoints`.
    - Construct a JSON object and call `AndroidReader.fixText(JSON.stringify(data))`.

### [Networking Layer]

#### [MODIFY] [FixService.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/FixService.kt)
- Update `improveText` signature to accept `context: String?` and `hotpoints: List<String>?`.
- Update the `POST /api/improve` request body to include these optional fields.

## Verification Plan

### Manual Verification
1. Create a highlight on a word (e.g., "домотканины").
2. Select the whole paragraph containing that word.
3. Click "Исправить".
4. Check logs or verify with the API that:
    - `text` contains the selected paragraph.
    - `context` contains surrounding paragraphs.
    - `hotpoints` contains the word "домотканины".
5. Verify that if there are no highlights or surrounding paragraphs, the API still works correctly (backwards compatibility).
