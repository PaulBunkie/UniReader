# Walkthrough: Save Improved EPUB to a New File

I have implemented a "Save As" flow to permanently store text improvements in a new EPUB file. This avoids permission issues when trying to overwrite original files.

## Changes Made

### New Navigation Flow
- Tapping the **Settings (⚙️)** button now opens a menu with English labels:
    - **Appearance**: Opens the font/theme settings.
    - **Save Updates**: Starts the process to save a new version of the book.

### "Save As" Implementation
- **File Picker**: When you select "Save Updates", the app opens a system dialog asking you where to save the new file and what to name it.
- **Background Processing**: The app reads from the original EPUB, applies all pending "green" improvements to the HTML chapters, and writes everything to the new destination.
- **Validity**: The resulting file is a valid EPUB (standard ZIP structure with correct `mimetype`).

### Technical Details
- **`EpubModifier.kt`**: Now takes a source and destination URI to perform the streaming copy and modification.
- **`ReaderActivity.kt`**: Integrated `ActivityResultContracts.CreateDocument` to handle the file creation and permission granting.

## How to Test
1. Create some "green" improvements in your current book.
2. Go to **Settings (⚙️)** -> **Save Improved Copy**.
3. Choose a folder and a name (e.g., `my_book_v2.epub`).
4. Wait for the "Saved successfully" message.
5. The original book remains untouched, and you have a new file with permanent fixes.
