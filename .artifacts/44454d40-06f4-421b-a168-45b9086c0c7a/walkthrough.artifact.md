# Walkthrough - Settings Improvements

I've implemented the requested settings: "Keep Screen On" for the reader and a "Target Translation Language" selector in the app settings.

## Changes Made

### Reader Settings
- **Keep Screen On**: Added a new toggle in the "Appearance -> General" menu of the reader.
    - When enabled, it prevents the device screen from turning off while reading.
    - Persisted in `ReaderSettings` and applied in `ReaderActivity` using `FLAG_KEEP_SCREEN_ON`.

### App Settings
- **Language Selector**: Redesigned the "Settings" screen (accessed from the main screen's top menu) to include a target language selector.
    - Supported languages: Auto (System), Russian, English, German, French, Spanish, Italian, Japanese, Chinese.
    - "Auto" mode automatically detects and uses the device's current system language.
- **Translation Integration**: The `TranslationManager` now retrieves the selected language from settings and passes it to the AI translation service.

## Components Modified

- [ReaderSettings.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/ReaderSettings.kt): Added `keepScreenOn` and `targetLanguage` fields.
- [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/ReaderActivity.kt): Implemented screen-on logic.
- [GeneralSettingsFragment.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/GeneralSettingsFragment.kt): Added UI binding for the screen-on toggle.
- [SecondFragment.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/SecondFragment.kt): Implemented the language selection UI and persistence.
- [TranslationManager.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/TranslationManager.kt): Updated to use the target language from settings.
- [MainActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/MainActivity.kt): Enabled navigation to the settings screen.

## Verification Results
- **Build**: Successfully compiled using `./gradlew app:assembleDebug`.
- **Logic**: Verified that the translation manager correctly computes the target language (either fixed or system-based) before sending requests.
