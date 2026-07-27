# Walkthrough: Reactive Translation & Prefetching Infrastructure

I have implemented the core orchestration logic for background translation, sequential prefetching, and the reactive UI overlays. This ensures a seamless reading experience without manually modifying any JavaScript or the WebView container.

## Key Backend Implementations

### 1. Translation Manager "Domino" Logic
- **Intelligent Queueing**: The `TranslationManager` now processes tasks one-by-one. It automatically starts prefetching the next two chapters (+1 and +2) as soon as the current focus is ready.
- **Priority Preemption**: If you navigate to an untranslated chapter, any ongoing background prefetch is cancelled immediately to focus all resources on the chapter you are currently viewing.
- **Task Callbacks**: Added `onActiveTaskChanged(index)` to allow the UI to react specifically to the manager's current activity.

### 2. Reactive UI Overlays (Native Android)
- **`processingOverlay`**: A small, centered semi-transparent card with a "Sand Clock" (ProgressBar). It appears only when the chapter you are looking at is actively being translated. It disappears the moment you swipe away or the task finishes.
- **`initialTranslationOverlay`**: A full-screen wait screen used only during the first open of a book to ensure the Table of Contents and the initial chapter are ready.

### 3. Seamless Content Refresh
- **Bridge Hook**: Integrated with the existing `onChapterEntered(index)` bridge. No JS changes were needed.
- **In-Place Swap**: When a chapter is ready, the app calls your standard `loadSpineItem(index)`. Because the file was swapped inside the EPUB ZIP in the background, the standard loader automatically displays the Russian version.

### 4. Mode Selection & Local Copying
- **Entry Point**: `MainActivity` now presents a "Original" vs "Translate & Read" dialog when adding a new book.
- **Safety First**: If translation is selected, the app creates a local copy of the EPUB in internal storage, ensuring the original source file remains untouched.

## Verification
- [x] **Build Status**: The project compiles successfully.
- [x] **Container Safety**: Verified that no code touches `webViewContainer` layout parameters or JS `injectIndexingScript`.
- [x] **Orchestration**: Confirmed that the manager handles priority and background tasks sequentially.

## How to Test
1. Clear App Data.
2. Add a book and select **"Translate & Read"**.
3. Wait for the initial prep screen to disappear.
4. Swipe through chapters:
    - If a chapter is ready, it loads instantly in Russian.
    - If you land on an English chapter, the "Sand Clock" will appear only when the manager starts working on it.
    - Swipe back to a Russian chapter, and the clock will vanish instantly.
