# Implementation Plan - Decoupled Image Viewer

As requested, I am moving the image viewer to a completely **separate WebView instance** inside a new Activity. This solves the "flying zoom" and "chapter jumping" issues by isolating the image viewing logic from the main reader's layout.

## Proposed Changes

### [NEW] [ImageViewerActivity](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ImageViewerActivity.kt)
*   A new Activity dedicated solely to displaying a single image.
*   Uses a **separate WebView instance** configured with built-in Android zoom controls (`setBuiltInZoomControls(true)`).
*   Handles `epub://` URIs to load images directly from the EPUB archive.
*   Provides a simple "Close" button (FAB) to return to the reader.

### [NEW] [activity_image_viewer.xml](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/res/layout/activity_image_viewer.xml)
*   A fullscreen layout with a WebView and a Floating Action Button for closing.

### [ReaderActivity](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
*   **REVERT:** Removed all complex JavaScript overlay, zoom math, and UI hiding logic.
*   **CLEANUP:** Removed unused `isImageViewerOpen` flags and related methods.
*   **NEW INTERFACE:** Added `AndroidReader.openImage(src)` which starts `ImageViewerActivity` via a standard Android Intent.
*   **STABILITY:** Since `ReaderActivity` no longer resizes when opening an image, the text layout remains stable, and no chapter jumps occur.

### [AndroidManifest.xml](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/AndroidManifest.xml)
*   Registered `ImageViewerActivity`.

## Verification Plan

### Manual Verification
1.  **Open Image:** Tap an image in a book. It should open in a new fullscreen window.
2.  **Zoom:** Use pinch-to-zoom. It will use the native Android WebView zoom, which is smooth and stable.
3.  **Close:** Tap the "X" button or use the system "Back" button.
4.  **Stability:** Verify that after closing, you are **exactly** where you were in the book, with no chapter jumps.
