# Walkthrough: Retry and Save Buttons for Text Improvement

I have added "Retry" (Обновить) and "Save" (Сохранить) buttons to the improvement overlay in `ReaderActivity`.

## Changes Made

### UI Updates
- **`activity_reader.xml`**: Added a button container (`fixActions`) with "Обновить" and "Сохранить" buttons at the bottom of the `fixOverlay`.
- Buttons are styled using Material 3 `TextButton` and `TonalButton` for a clean, modern look.

### Logic Updates
- **`ReaderActivity.kt`**:
    - **State Management**: Added `lastFixRequestJson` to remember the last selection, context, and hotpoints.
    - **Retry Logic**: Clicking "Обновить" re-triggers the API call using the saved request data.
    - **Save Placeholder**: Clicking "Сохранить" shows a Toast message as a placeholder for the future saving implementation.
    - **Visibility Handling**: The buttons are hidden while the API is loading and shown once a response (or error) is received.

## How to Test
1. Select a paragraph in a book and click **"Исправить"**.
2. Wait for the improvement result.
3. Observe the **"Обновить"** and **"Сохранить"** buttons appearing at the bottom of the card.
4. Click **"Обновить"** to see the task restart and fetch a new version.
5. Click **"Сохранить"** to see the placeholder message.
