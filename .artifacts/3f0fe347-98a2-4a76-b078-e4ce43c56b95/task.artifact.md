# Task List (WAITING FOR APPROVAL FOR PENDING TASKS)

> [!IMPORTANT]
> **RULE:** No implementation or file modification without explicit user permission.

- [x] Add overlay UI to `activity_reader.xml`
- [x] Update `ReaderActivity.kt`:
    - [x] Add `JavascriptInterface` method `fixText(text: String)`
    - [x] Update `injectIndexingScript()` to support two buttons ("Пометить", "Исправить")
    - [x] Implement UI logic to show/hide the overlay
    - [x] Add placeholder for API call
- [x] Integrate real API with polling (Battle URL: http://136.109.52.87:8080/api)

---

## PENDING (Drafting mode - DO NOT EXECUTE)

- [ ] **Batch Improvement flow**:
    - [ ] Update `activity_reader.xml`: add "Обновить" and "Принять" buttons to overlay
    - [ ] Update `HighlightDatabase.kt`: ensure `replacement_text` is handled correctly
    - [ ] Update `ReaderActivity.kt`:
        - [ ] Add logic for "Refresh" (retry API) and "Accept" (save to DB)
        - [ ] Update JS `applyHighlights` to perform text replacement in DOM
    - [ ] Add "Apply all fixes to file" to `menu_reader.xml`
    - [ ] Create `EpubModifier.kt` for batch writing to EPUB
    - [ ] Implement menu action in `ReaderActivity.kt` to trigger batch commit
- [ ] Verify persistence and batch commit
