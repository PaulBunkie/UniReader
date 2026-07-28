# Localization of UI to Russian and English Standardization

The goal is to add Russian localization to the UniReader app and standardize the default resources to English. This involves extracting hardcoded strings from layouts and Kotlin code, ensuring all default strings are in English, and creating a Russian translation in `res/values-ru/strings.xml`.

## User Review Required

> [!IMPORTANT]
> Currently, some strings in the layouts and default `strings.xml` are in Russian. I will move them to the Russian-specific resource file and replace them with English equivalents in the default `strings.xml`.

## Proposed Changes

### [Component Name] UI Resources

#### [MODIFY] [strings.xml](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/res/values/strings.xml)
- Add missing strings from code and layouts.
- Translate existing Russian strings (like `brightness_format`) to English.

#### [NEW] [strings.xml (ru)](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/res/values-ru/strings.xml)
- Create Russian translations for all strings, including those moved from default resources.

### [Component Name] Layouts
#### [MODIFY] Multiple layout files
- Replace hardcoded `android:text="..."` (both English and Russian) with `android:text="@string/..."`.
- Ensure all hardcoded Russian text is translated to English for the default resource reference.

### [Component Name] Kotlin Code
#### [MODIFY] Multiple Kotlin files
- Replace hardcoded strings in `Toast`, `AlertDialog`, `Log`, etc., with `getString(R.string....)` or string resources.
- Translate Russian error messages and UI text to English in the default resource file.

## Verification Plan

### Automated Tests
- Build the project to ensure all resource references are valid.
- Run existing tests (if any) to ensure no regressions.

### Manual Verification
- Change device language to Russian and verify that the UI is translated.
- Verify that English remains the default for other languages.
- Check specific UI elements like dialogs, toasts, and settings fragments.
