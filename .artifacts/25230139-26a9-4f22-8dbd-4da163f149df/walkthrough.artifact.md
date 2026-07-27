# Walkthrough - API Logging Overlay

I have implemented a real-time API logging overlay to help you monitor what the application is doing in the background.

## Key Features

### 1. Real-time API Log Overlay
A semi-transparent black overlay has been added to the reader. It displays:
- **Requests**: Chapter translation requests with a preview of the text being sent.
- **Tasks**: IDs of tasks created on the server.
- **Results**: Status of the requests (Success/Error) and previews of received data.
- **Fixes**: Status of the "Improve Text" requests.

### 2. Toggleable Visibility
To keep the reading experience clean, the log is hidden by default. You can toggle it:
1. Open the **Reader Settings** (gear icon in the top right).
2. Click **"Показать API Лог"**.

### 3. Automatic Scrolling
The log automatically scrolls to the bottom as new events are added, so you always see the latest activity.

## Implementation Details

### [DebugLogger.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/DebugLogger.kt)
A new utility class that manages the log entries and notifies the UI when updates occur.

### [activity_reader.xml](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/res/layout/activity_reader.xml)
Added a `ScrollView` + `TextView` overlay positioned above the bottom panel.

### [TranslationService.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/TranslationService.kt) & [FixService.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/FixService.kt)
Instrumented these services to report their activity to the `DebugLogger`.

## How to Test
1. Open any book in translation mode.
2. Go to the settings menu and click **"Показать API Лог"**.
3. Scroll through the book or use the "Improve Text" feature.
4. **Observation**: You will see detailed logs of each request and response appearing in real-time.
