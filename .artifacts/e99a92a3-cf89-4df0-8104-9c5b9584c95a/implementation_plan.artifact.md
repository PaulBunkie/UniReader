# Implementation Plan - Text Highlighting with External Storage

This plan covers the implementation of text highlighting in the reader. Highlights will be stored in an external database (SQLite) and re-applied whenever a chapter is loaded.

## User Review Required

> [!NOTE]
> Мы будем использовать SQLite (через Room или напрямую) для хранения выделений. Это позволит быстро искать выделения для конкретной главы при её загрузке.

> [!TIP]
> Для подсветки мы будем использовать тег `<mark>` с кастомными CSS-стилями, которые адаптируются под светлую и темную темы.

## Proposed Changes

### [Reader UI & JS Bridge]

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt)
*   **Custom Context Menu:** Установить `webView.customSelectionActionModeCallback`. В методе `onPrepareActionMode` добавить пункт "Выделить" первым в списке.
*   **JS Selection Logic:** Добавить JS-функцию `getSelectionDetails()`, которая будет вызываться при клике на "Выделить". Она должна возвращать `spineIndex`, `elementIdx`, `startOffset`, `endOffset` и `text`.
*   **JavascriptInterface:** Добавить метод `saveHighlight(json: String)` для получения данных из JS.
*   **Highlight Application:** После загрузки каждой главы запрашивать из БД список выделений и вызывать JS-функцию `applyHighlights(json)`.

### [Data Layer]

#### [NEW] [HighlightModel.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/HighlightModel.kt)
*   Определение сущности `Highlight`:
    *   `id`: Long (PK)
    *   `bookUri`: String
    *   `spineIndex`: Int
    *   `elementIdx`: Int
    *   `startOffset`: Int
    *   `endOffset`: Int
    *   `originalText`: String
    *   `note`: String? (для будущих исправлений)
    *   `color`: String (например, желтый/оранжевый)

#### [NEW] [HighlightDatabase.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/HighlightDatabase.kt)
*   Реализация SQLite/Room для хранения выделений.

### [Styling]

#### [MODIFY] [ReaderActivity.kt](file:///C:/Users/Владелец/AndroidStudioProjects/UniReader/app/src/main/java/com/example/unireader/ReaderActivity.kt) (внутри `initPagedView` CSS)
*   Добавить CSS для тега `mark.uni-highlight`:
    *   Light mode: `background-color: #ffeb3b; color: #000;`
    *   Dark mode: `background-color: #f57f17; color: #fff;` (более темный/насыщенный оранжевый для контраста).

## Verification Plan

### Automated Tests
*   Unit-тесты для `HighlightDatabase` (сохранение/получение).

### Manual Verification
1.  Открыть книгу, выделить текст.
2.  Вызвать команду "Выделить" (через кнопку или меню).
3.  Убедиться, что текст подсветился.
4.  Закрыть книгу, открыть снова на той же главе -> подсветка должна быть на месте.
5.  Переключить тему (светлая/темная) -> проверить читаемость.
