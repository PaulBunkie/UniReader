# Implementation Plan: Add "Retry" and "Save" Buttons to Improvement Overlay

This plan adds two buttons to the "Improvement" overlay to allow the user to re-trigger the API request or save the result.

## Proposed Changes

### [ReaderActivity UI]

#### [MODIFY] [activity_reader.xml](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/res/layout/activity_reader.xml)
- Add a `LinearLayout` container at the bottom of the `fixOverlay` (inside the vertical `LinearLayout`).
- Add a "Retry" button (label: "Обновить").
- Add a "Save" button (label: "Сохранить").
- Initially hide this button container (`android:visibility="gone"`).

### [ReaderActivity Logic]

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- Declare new properties for the buttons and the button container.
- Declare a property `lastFixRequestJson: String?` to store the last request data.
- In `onCreate`, bind the new views and set click listeners:
    - **Refresh Button**: Calls `showFixOverlay(lastFixRequestJson!!)` to retry the same request.
    - **Save Button**: Shows a placeholder Toast ("Сохранение будет реализовано позже").
- In `showFixOverlay`:
    - Store the incoming `json` into `lastFixRequestJson`.
    - Hide the button container while loading starts.
    - Show the button container once the API call finishes (either `onSuccess` or `onError`).

## Verification Plan

### Manual Verification
1. Select text and click "Исправить".
2. Verify that while loading, only the progress bar and status text are visible.
3. Once the result is received, verify that "Обновить" and "Сохранить" buttons appear.
4. Click "Обновить" and verify that the request is sent again (loading appears).
5. Click "Сохранить" and verify the placeholder Toast message appears.
