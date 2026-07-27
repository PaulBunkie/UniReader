# Implementation Plan - Update Translation Contract to JSON Glossary

Update the translation request/response contract to use JSON objects for the glossary and user corrections, moving away from Markdown tables.

## User Review Required

> [!IMPORTANT]
> **Data Migration**: Existing books with Markdown-based glossaries will have their glossaries reset to empty because the new parser expects a JSON structure. Since the glossary is re-generated and updated by the server, this should not be a critical issue for users.

## Proposed Changes

### [Metadata Layer] BookMetadata.kt & LibraryProvider.kt
- **`BookMetadata.kt`**: Rename `translationGuidelines` to `serverGlossary` to reflect its new JSON nature.
- **`LibraryProvider.kt`**: Update serialization to use the key `"serverGlossary"`. Include fallback logic to read the old `"translationGuidelines"` key if the new one is missing, but treat it as null if it doesn't look like JSON.

### [Service Layer] TranslationService.kt
- **`translateChapter`**:
    - Change parameter `guidelines: String?` to `glossary: JSONObject?` and `userCorrections: JSONArray?`.
    - Update request body construction:
        ```json
        {
          "glossary": { ... },
          "user_corrections": [ ... ]
        }
        ```
    - Update response parsing to extract the updated `"glossary"` object and return it as a stringified JSON.

### [Logic Layer] TranslationManager.kt
- **`processTranslation`**:
    - Parse `currentMeta.serverGlossary` into `JSONObject`.
    - Create a helper to convert `dictEntries` (Highlights) into the expected `user_corrections` `JSONArray`.
    - Pass these JSON structures to the updated `TranslationService`.
- **`buildGuidelines`**: Delete this method as it's no longer needed for Markdown tables.

### [UI Layer] ServerGlossarySheet.kt
- **`onCreateView`**: Add logic to pretty-print the JSON glossary for better readability in the bottom sheet.

## Verification Plan

### Manual Verification
1. **Logcat Monitoring**:
    - Filter by `TranslationService` and `TranslationManager`.
    - Verify that the outbound JSON has the correct `glossary` and `user_corrections` fields.
    - Verify that `user_corrections` contains the words added to the local dictionary.
2. **Glossary Verification**:
    - Open "Глоссарий сервера" in the reader menu.
    - Verify it displays the JSON glossary returned by the server in a readable format.
3. **Translation Success**:
    - Verify that the chapter text is correctly translated using the provided glossary/corrections.
