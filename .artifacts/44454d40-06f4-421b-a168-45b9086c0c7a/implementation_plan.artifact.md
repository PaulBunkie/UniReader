# Implementation Plan - Settings Improvements

Add "Keep Screen On" option to General settings and a language selector for translations in the App Settings.

## User Review Required

> [!IMPORTANT]
> The target language selector will affect all **future** translations. Already translated chapters in existing books will not be automatically re-translated unless the user chooses the "Re-translate Chapter" option manually for each chapter.

> [!NOTE]
> Gemini and GPT support a vast number of languages. I will provide a selection of the most common ones, including a "System Language" option that automatically detects your device's language.

## Proposed Changes

### Core Settings

#### [MODIFY] [ReaderSettings.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/ReaderSettings.kt)
- Add `keepScreenOn: Boolean` (default: `false`).
- Add `targetLanguage: String` (default: `"Russian"`).
- Update `load()` and `save()` to persist these values.

---

### Reader Experience

#### [MODIFY] [fragment_general_settings.xml](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/res/layout/fragment_general_settings.xml)
- Add a `MaterialSwitch` for "Keep Screen On" under the Fullscreen option.

#### [MODIFY] [GeneralSettingsFragment.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/GeneralSettingsFragment.kt)
- Bind the new switch to `settings.keepScreenOn`.
- Call `activity.applyKeepScreenOn(isChecked)` when toggled.

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/ReaderActivity.kt)
- Implement `applyKeepScreenOn(enabled: Boolean)` using `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`.
- Call this method in `onCreate()` to ensure the setting is applied on start.

---

### App Settings & Translation

#### [MODIFY] [fragment_second.xml](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/res/layout/fragment_second.xml)
- Redesign the layout to include a "Translation Language" section.
- Add a `Spinner` or `AutoCompleteTextView` for language selection.

#### [MODIFY] [SecondFragment.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/SecondFragment.kt)
- Populate the language selector with a list of supported languages.
- Handle "System Language" logic (detecting current locale).
- Save selection to `ReaderSettings`.

#### [MODIFY] [TranslationManager.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/TranslationManager.kt)
- Retrieve `targetLanguage` from `ReaderSettings` and pass it to `TranslationService` methods.

#### [MODIFY] [TranslationService.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/TranslationService.kt)
- Ensure `translateTOC` and `translateChapter` use the `targetLanguage` parameter in the API request body.

---

### Resources

#### [MODIFY] [strings.xml](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/res/values/strings.xml)
- Add strings for:
    - `keep_screen_on`
    - `target_translation_language`
    - `system_language`
    - List of language names.

## Verification Plan

### Automated Tests
- I will check if the code builds successfully using `./gradlew assembleDebug`.

### Manual Verification
1.  **Keep Screen On**:
    - Open a book, go to Appearance -> General.
    - Toggle "Keep Screen On".
    - Verify that the screen does not dim (can be checked via `adb shell dumpsys power` if needed, but manual check is usually enough).
2.  **Language Selection**:
    - Go to App Settings from the main screen.
    - Change target language (e.g., to "German").
    - Open a new book or re-translate a chapter.
    - Verify that the translation request sent to the server contains `target_language: "german"`.
