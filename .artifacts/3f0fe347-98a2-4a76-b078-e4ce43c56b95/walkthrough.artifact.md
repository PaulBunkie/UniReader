# Walkthrough: Text Improvement Enhancements

I have updated the text improvement feature to include more context for better results and display additional information about the model used.

## Changes Made

### API Integration (Context & Hotpoints)
- **`FixService.kt`**: Updated to support optional `context` and `hotpoints` fields in the `/api/improve` request.
- **`ReaderActivity.kt`**:
    - Updated JavaScript injection to automatically gather surrounding paragraph context.
    - Added logic to detect existing highlights ("hotpoints") within the selection and send them to the API.

### UI Enhancements
- **`activity_reader.xml`**:
    - Added "Retry" (Обновить) and "Save" (Сохранить) buttons to the overlay.
    - Added a small, dimmed `TextView` (`tvFixModel`) to display the AI model's name.
- **`ReaderActivity.kt`**:
    - Implemented retry logic using `lastFixRequestJson`.
    - Added a placeholder action for the "Save" button.
    - Updated the UI to display the model name (e.g., `claude-3-haiku`) received from the API response.

### Networking
- Updated the base URL to the production server: `http://136.109.52.87:8080/api`.

## How to Test
1. Select a paragraph that contains an existing highlight and click **"Исправить"**.
2. Verify that the overlay shows "Processing...".
3. Once completed, verify:
    - The improved text is displayed.
    - The name of the model that generated the response is shown at the bottom right of the text (small and dimmed).
    - **"Обновить"** and **"Сохранить"** buttons are visible.
4. Click **"Обновить"** to request a new version from the API.
