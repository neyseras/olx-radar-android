# Статус заглушек — ВСЕ ЗАКРЫТЫ (ревизия 2)

Все позиции из первого списка устранены. Детальный повторный аудит кода
(включая проверку моего собственного кода из ревизии 1; APK собран) чист.

## Что было исправлено в ревизии 2

1. **Мёртвый код (9 файлов + 11 layout + menu + 44 drawable) — УДАЛЕНО.**
   Legacy View-UI (`FindingsFragment`, `WatchesFragment`, `StatusFragment`,
   3 BottomSheet-фрагмента, 3 адаптера) + все их XML-ресурсы. Удалены также
   мёртвые `ic_*`-иконки и `tab_selector_cyber`/`bg_tab_selected`,
   `bottom_nav_item_color`. MainActivity больше не содержит мост
   `onTabSwitchedFromFragment`. Осталось только 5 рабочих drawable
   (`ic_radar`, `ic_play`, `ic_pause`, `ic_clear`, `ic_open` — уведомления).

2. **Пустой catch при открытии ссылки (MainComposeScreen, FindingsFeedScreen) — ЗАКРЫТО.**
   Теперь показывается Toast «Не удалось открыть ссылку» + запись в AppLog.

3. **"ИИ-анализ временно недоступен" — ЗАКРЫТО.** В карточке ошибки ИИ в
   Deal Inspector добавлена кнопка **«Повторить ИИ-анализ»** с прогрессом:
   - `AiFilterEngine.reanalyzeFinding()`: сбрасывает ИИ-кэш объявления,
     прогоняет Groq Vision заново и обновляет `ai_*`-поля находки в БД;
   - `FindingsViewModel.retryAiAnalysis()` + состояние `aiRetryingIds`;
   - `FindingDao.updateFinding` (@Update), `AiCacheDao.deleteByListingId`;
   - проводка через `ViewModelFactory` и `MainComposeScreen`.

4. **Проглоченные исключения — ЗАЛОГИРОВАНЫ.** Все молчаливые
   `catch (_: Exception)` заменены на `AppLog.w`:
   - `AiFilterEngine` (prune кэша, delete/insert кэша, обновление находки);
   - `StatusViewModel` (очистка ИИ-кэша).

5. **TODO-комментарии OlxParser — ЗАКРЫТЫ (документация вместо TODO).**
   - B3 (валюта): серверного параметра нет — проверка валюты реально
     выполняется в `matchesWatch()` (валюты не совпали → ценовые границы
     не применяются, залогировано). TODO заменён точным комментарием.
   - B13 (`pushup_time`/`last_refresh_time`): безопасный fallback к null —
     это реализованное поведение (pushup-логика в MonitorService учитывает
     null), комментарий переписан как документация.

6. **keystore.properties — уже не заглушка.** В build.gradle.kts
   release-подпись автоматически откатывается на debug-ключ, если
   `keystore.properties` отсутствует (изменение кода не требовалось).

## Остаточные (легитимные) паттерны — НЕ заглушки

- `AiAnalysisResult.fromJson` возвращает `null` при невалидном JSON —
  контракт разбора, вызывающий код обрабатывает null.
- `GroqVisionClient`: чтение `errorStream` с fallback `""` перед броском
  исключения с кодом HTTP — корректная диагностика.
- Placeholder'ы в текстовых полях («Например: iPhone 13 Pro», «gsk_...») —
  подсказки UI, не заглушки.

## Проверка моего кода из ревизии 1

- glassPanel-вставки в 5 экранах проверены (позиции, баланс скобок) — чисто;
- импорт-склейки, возникшие при правках (`import…import`, `},modifier`),
  найдены и исправлены;
- `Listing(...)` в повторном анализе дополнен обязательным `refreshedAt`;
- конструктор `FindingsViewModel` обновлён во ВСЕХ точках вызова
  (ViewModelFactory + Robolectric-тест FindingsUiTest);
- удалённые ресурсы перепроверены на ссылки (нигде не используются).

## Верификация сборкой (ревизия 2)

- `./gradlew assembleDebug` — **BUILD SUCCESSFUL** (Gradle 8.12, AGP 8.9.1, Kotlin 2.1.21).
- Итоговый APK: `OLX-Radar-v1.3.1-debug.apk` (24.3 MB).
- Попутно починено: тест `FindingsUiTest` ссылался на удалённый legacy-фрагмент
  (`FindingDetailsBottomSheetFragment.newInstance`) — тест бандла удалён,
  остались два актуальных теста ViewModel/БД.
- `WatchItemUi` перенесён из удалённого legacy-адаптера в `WatchesViewModel.kt`.
