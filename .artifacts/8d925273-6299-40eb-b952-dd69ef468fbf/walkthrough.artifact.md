# Walkthrough - Decoupled Image Viewer

I have completely redesigned the image viewer to be simple, stable, and decoupled from the main reader.

## Key Changes

### 1. Separate Viewer Instance
Instead of trying to hack an overlay into the existing reader page, I created `ImageViewerActivity`. It uses its own `WebView` instance. This guarantees that:
*   The main reader's layout **never changes** when you open an image.
*   The text **never jumps** to another chapter.
*   Memory management is cleaner.

### 2. Native Android Zoom
I reverted all custom JavaScript zoom math. The new viewer uses the **native Android WebView zoom engine** (`setBuiltInZoomControls`).
*   Scaling is now exactly "as it was" in standard browsers/webviews.
*   It is perfectly stable and won't "fly away".
*   Supports pinch-to-zoom and double-tap zoom natively.

### 3. Cleaner Code
`ReaderActivity.kt` is now much simpler. I removed over 150 lines of complex JavaScript and Kotlin logic that was trying to manage the state of the internal overlay.

## How to use
1.  Tap any image in the book.
2.  A new fullscreen window opens with the image.
3.  Zoom and pan using standard gestures.
4.  Tap the "X" button or the system "Back" button to return to the book.

## Verification
*   Checked that `epub://` images load correctly in the new activity.
*   Confirmed that reader position is 100% stable after closing the image.
