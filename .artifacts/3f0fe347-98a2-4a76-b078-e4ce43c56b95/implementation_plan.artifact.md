# Implementation Plan: Robust Queue & Reactive Refresh

This plan fixes the translation stalls and ensures all server tasks are completed and saved. It respects the core reader logic by using standard file-based loading and avoids redundant task cancellations.

## User Review Required

> [!IMPORTANT]
> **No More Wasted Tasks:**
> - The `TranslationManager` will now use a persistent task pool (`activeTasks`).
> - Moving to another chapter will **not** cancel ongoing translations. The app will finish polling and save every chapter it starts.
>
> **Core Reader Fidelity:**
> - The app will continue to read content directly from the local EPUB copy.
> - Background tasks will swap the XHTML entries in that copy.
> - The UI will refresh using the standard `loadSpineItem` call once the file is ready.

## Proposed Changes

### [Translation Orchestration - TranslationManager.kt]

#### [MODIFY] [TranslationManager.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/TranslationManager.kt)
- **Persistent Pool**: Replace `currentJob` with `activeTasks: ConcurrentHashMap<Int, Job>`.
- **Logic Refinement**:
    - `queueTranslation(index)` check: If `index` is in `activeTasks`, do nothing.
    - `onChapterVisible(index)`: Ensure `index`, `index+1`, and `index+2` are queued.
- **Initial Load**: Queue TOC, Ch 0, Ch 1, and Ch 2 immediately upon initialization.

### [Reader Logic - ReaderActivity.kt]

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- **Responsive Startup**:
    - `initTranslation`: Wait ONLY for Chapter 0 (the one on screen). As soon as it's ready, hide the initial overlay to let the user swipe.
- **Native Refresh**:
    - In `onChapterReady(index)` callback: If `index == currentSpineIndex`, call `loadSpineItem(index)`.
- **Reactive Clock**:
    - `processingOverlay.visibility` will be `VISIBLE` only if `currentSpineIndex` is a key in the manager's `activeTasks` map.

## Verification Plan

### Manual Verification
1. Open a new book -> Wait for Ch 0 -> Overlay disappears.
2. Swipe to Ch 1 (Original).
3. Watch logs -> Verify Ch 1, Ch 2, and Ch 3 are being processed simultaneously or sequentially without cancellations.
4. Stay on Ch 1 -> Verify it "turns" Russian automatically without you having to re-open the book.
5. Verify "Sand Clock" appears only when the active chapter is being translated.
