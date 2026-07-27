# Task List - Update Translation Contract to JSON

- [x] **Metadata Layer Updates**
    - [x] Update `BookMetadata.kt`: Rename `translationGuidelines` -> `serverGlossary`
    - [x] Update `LibraryProvider.kt`: Handle migration and new field name
- [x] **Service Layer Updates**
    - [x] Update `TranslationService.kt`: Implement JSON request/response contract
- [x] **Logic Layer Updates**
    - [x] Update `TranslationManager.kt`:
        - [x] Parse/Serialize JSON for glossary
        - [x] Implement `user_corrections` generation from highlights
        - [x] Remove `buildGuidelines` (Markdown-specific)
- [x] **UI Layer Updates**
    - [x] Update `ReaderActivity.kt`: Fix menu item for the new field
    - [x] Update `ServerGlossarySheet.kt`: Add JSON pretty-print formatting
- [x] **Verification**
    - [x] Verify outbound JSON in Logcat
    - [x] Verify server response ingestion
    - [x] Verify glossary display in UI
