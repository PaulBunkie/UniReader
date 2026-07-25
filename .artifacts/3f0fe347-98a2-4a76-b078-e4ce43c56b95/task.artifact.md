# Task List: Save Improved EPUB to a New File

- [x] Modify `EpubModifier.kt` to support source and destination URIs
- [x] Update `ReaderActivity.kt`:
    - [x] Add `saveDocumentLauncher` using `ActivityResultContracts.CreateDocument`
    - [x] Update `PopupMenu` labels to English ("Appearance", "Save Updates")
    - [x] Implement `performSave(destinationUri)` logic
- [x] Verify "Save As" flow
