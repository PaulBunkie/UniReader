# Fix Fullscreen and Reading Mode State Restoration

The goal is to fix the issue where the full-screen mode and reading mode (paged/seamless) are "forgotten" during state restoration or book loading.

## User Review Required

> [!IMPORTANT]
> I am adding `isFullscreen` to `ReaderSettings` to make this preference persistent across app restarts. Currently, it was only kept in `savedInstanceState`.

## Proposed Changes

### [ReaderSettings](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderSettings.kt)

#### [MODIFY] [ReaderSettings.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderSettings.kt)
- Add `isFullscreen: Boolean = false` to the data class.
- Update `load()` and `save()` to handle the new property.

### [ReaderActivity](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
- Initialize `isFullscreenPref` and `isPagedMode` from `settings` in `onCreate`.
- Ensure `savedInstanceState` overrides settings for session-specific state (like current position, but maybe not for global modes like fullscreen/paged if they are already in settings). Actually, `savedInstanceState` should have higher priority for restoration during a session.
- Fix `isPagedMode` initialization which was completely missing in `onCreate`.
- Update `toggleFullscreenExternally` and `setReadingMode` to save changes to `settings`.
- Improve `updateUiState` reliability by ensuring it's called properly and handles the window insets correctly even during early lifecycle stages. Use `window.decorView.post` for system bar hiding to ensure the view is attached.

## Verification Plan

### Manual Verification
1. **Test Fullscreen Persistence**:
    - Enable Fullscreen in settings.
    - Close and reopen the app. Verify it's still in Fullscreen.
    - Rotate the screen. Verify Fullscreen is maintained.
2. **Test Reading Mode Persistence**:
    - Switch to Seamless mode.
    - Rotate the screen. Verify it stays in Seamless mode.
    - Close and reopen the app. Verify it's still in Seamless mode.
3. **Test "Forgot Fullscreen during Loading"**:
    - Open a book in Fullscreen.
    - Navigate to another chapter. Verify Fullscreen is maintained.
    - Perform a configuration change (rotation) while a chapter is loading. Verify Fullscreen is maintained.
