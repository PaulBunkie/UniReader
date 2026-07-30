# Обновление настроек по умолчанию, UI и автоинкремент билда

Необходимо обновить значения настроек по умолчанию для читалки, убрать упоминание "dp" из интерфейса и настроить автоматическое увеличение номера сборки (`versionCode`), чтобы не забывать об этом при релизе.

## Proposed Changes

### 1. Изменение настроек по умолчанию в коде
Обновим значения в классе `ReaderSettings`.

#### [MODIFY] [ReaderSettings.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/reaido/unireader/ReaderSettings.kt)
- `fontSize` = 18
- `paragraphSpacing` = 0.4f
- `lineHeight` = 1.4f
- `firstLineIndent` = 0.5f
- `columnGap` = 2
- `paddingLeft` = 10
- `paddingRight` = 9
- `isProdApi` = true

### 2. Обновление текстовых ресурсов
Уберем "(dp)" из строки "Отступы экрана".

#### [MODIFY] [strings.xml (RU)](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/res/values-ru/strings.xml)
- `screen_margins`: "Отступы экрана (dp)" -> "Отступы экрана"

#### [MODIFY] [strings.xml (EN)](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/res/values/strings.xml)
- `screen_margins`: "Screen Margins (dp)" -> "Screen Margins"

### 3. Автоинкремент versionCode [NEW]
Добавим механизм, который будет автоматически увеличивать `versionCode` при каждой релизной сборке. Мы создадим файл `version.properties` и добавим скрипт в Gradle.

#### [NEW] [version.properties](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/version.properties)
- Файл для хранения текущего `versionCode`.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/build.gradle.kts)
- Добавление логики чтения и инкремента версии.

## Verification Plan

### Automated Tests
- Запуск сборки релизной версии и проверка, что `versionCode` увеличился.

### Manual Verification
- Проверка настроек в приложении.
- Проверка отсутствия "(dp)".
