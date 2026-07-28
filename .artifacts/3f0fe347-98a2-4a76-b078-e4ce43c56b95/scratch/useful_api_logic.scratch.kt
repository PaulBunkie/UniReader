// --- NETWORKING: FixService.kt ---
/*
Handles POST /api/improve with context and hotpoints.
Polling logic included.
*/

// --- NETWORKING: TranslationService.kt ---
/*
Handles:
1. POST /api/translate/toc (Text-based TOC translation)
2. POST /api/translate (Chapter translation with guidelines)
3. GET /api/translate/<task_id> (Polling)
*/

// --- ORCHESTRATION: TranslationManager.kt ---
/*
- Priority-based queue (Single Job).
- Glossary (Guidelines) assembly: Markdown table with [DICT_P] and [DICT_C] entries.
- Automated chapter-by-chapter prefetching.
- Integration with EpubModifier for ZIP injection.
*/

// --- FILE OPS: EpubModifier.kt ---
/*
- createLocalCopy(sourceUri)
- replaceEntry(epubUri, entryName, content) -> Safe ZIP replacement maintaining mimetype integrity.
- applyFixes(book, fixes, destinationUri) -> Batch HTML replacement via Jsoup XML parser.
*/

// --- DATABASE: HighlightDatabase.kt ---
/*
- TABLE highlights: support for [DICT_P]: and [DICT_C]: prefixes.
- TABLE chapter_translation_status: tracking translated state.
*/

// --- JS SNIPPETS ---
/*
1. Selection Context: capture 1000 chars before/after using document.createRange().
2. Hotpoints: querySelectorAll('.uni-highlight') within range. cloneContents() into temp div for parsing.
3. Tooltips: #uni-fix-tooltip with automatic bounding box positioning.
4. Dict+: [DICT_P] vs [DICT_C] visual separation and deletion locking.
*/
