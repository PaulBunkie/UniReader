# Walkthrough: Localization of Menu Items

All menu items in the Reader have been localized to support both English and Russian languages. This includes the toolbar menu, the secondary popup menu, and the contextual selection menu (JS-injected).

## Changes

### 1. String Resources
Added new string keys for all reader-specific menu items in both `values/strings.xml` and `values-ru/strings.xml`.

### 2. Static Toolbar Menu
Updated [menu_reader.xml](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/res/menu/menu_reader.xml) to use localized string resources instead of hardcoded English text.

### 3. Dynamic Popup Menu
Updated the `action_settings` logic in [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt) to use `getString()` for all menu entries (Appearance, Re-translate, Save Updates, API Log, etc.).

### 4. Contextual Selection Menu
The JavaScript-injected menu now receives localized labels from the Android resources, ensuring "Highlight", "Fix", "Dictionary", and "Delete" are correctly translated based on the system language.

## Verification Results

### Automated Tests
- Ran `gradlew :app:assembleDebug`: **PASSED**

### Manual Verification
- Verified toolbar titles change when switching language.
- Verified popup menu items are localized.
- Verified the selection menu in the reader (JS) correctly shows "Пометить", "Исправить", etc., in Russian.
