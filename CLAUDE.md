# DASEIN — правила проекта

Android-приложение: `android/`, Kotlin + Jetpack Compose, пакет `com.dasein.poryadok`.

## Работа с репозиторием
- Только ветка `claude/relaxed-newton-2kl55r`; пуш: `git push -q origin claude/relaxed-newton-2kl55r`. Без PR.
- Коммит с `[release]` в сообщении собирает APK в CI; новый пуш отменяет идущую сборку.
- Ссылка на сборку: `https://github.com/dasein00/outfit-creator/releases/download/android-v1.0.N/DASEIN.apk`.
- После сборки отвечать по-русски: номер версии и ссылка.
- Никаких личных данных в коде.

## Интерфейс
- Общие компоненты — `ui/common` (Screen, Tile, SectionTitle, Pill, Button, Segments, TextInput, InfoBox…).
- Тексты интерфейса подменяются через `assets/ui_texts.tsv` и `UiTexts` — не ломать: надписи выводить через `ui.common.Text`/`btnLabel`.
- Иконки и функционал без просьбы не менять.

## Системные полосы Android — обязательно
Ни одна кнопка, панель, поле или ползунок не должны заходить под строку состояния и под системные кнопки
«Назад / Домой / Недавние» (или полоску жестов). Приложение работает edge-to-edge (`enableEdgeToEdge()`), поэтому:
- Обычный экран — в `Scaffold`, содержимое обязательно получает его `pad` (`Modifier.padding(pad)`).
- Полноэкранные окна (`Dialog` с `decorFitsSystemWindows = false`) и всё, что прижато к низу экрана вне `Scaffold`, —
  через `Modifier.safeBars()` / `Modifier.safeBottom()` из `ui/common/SafeBars.kt`. Внутри Dialog обычный
  `systemBarsPadding()` на части телефонов даёт 0 — его там не использовать.
- Списки, в конце которых кнопки, — с нижним запасом (`contentPadding`/`Gap`), чтобы последний элемент не прятался.
- Новые нижние панели проверять по этому правилу до коммита.

## Экранная клавиатура — обязательно
Поле, в которое печатают, всегда должно быть видно над клавиатурой.
- Общий `Screen()` уже даёт в `pad` отступ от клавиатуры (`contentWindowInsets = systemBars ∪ ime`) и помечает его
  учтённым — содержимое с полями должно быть прокручиваемым (`verticalScroll`/`LazyColumn`) и получать `pad`;
  тогда фокусное поле само подтягивается над клавиатурой.
- Свои `Scaffold` — с тем же `contentWindowInsets`. Полноэкранные окна с полями — `Modifier.safeBars(withIme = true)`.
- Поле внизу экрана вне `Scaffold` (чат, строка ввода) — `imePadding()`.
