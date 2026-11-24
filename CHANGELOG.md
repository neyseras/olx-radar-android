# Changelog — OLX Radar Android

## Версия 2611081 (`versionCode: 261120081`)

### Готовые сборки (APK)
- **Release (R8 + Resource Shrinking):** `releases/app-release.apk`
- **Debug:** `releases/app-debug.apk`

---

### Полный редизайн, UI и оптимизация производительности
- **Обновлённый визуальный стиль и темы:**
  - Добавлена 7-я тема `AMOLED` и исправлена контрастность светлых/тёмных тем (`WHITE`, `GRAY`, системные бары через `SideEffect`).
  - Созданы общие компоненты в `DesignComponents.kt`: `AmbientRadarBackdrop` (с кэшированием градиентов в `drawWithCache`), `RadarSurfaceCard`, `StatusPulseDot`, `DealScoreBadge`, `MetricTile`, `CachedFormatters`.
- **Плавность и устранение лагов:**
  - Заменено пересоздающее экраны поведение `Crossfade` в `MainComposeScreen` на кэширование посещённых вкладок (`visitedTabs`) с пропуском разметки/отрисовки и фоновых анимаций для скрытых экранов.
  - Экран настроек `StatusAiSettingsScreen` переведён с eager-`Column` на ленивый `LazyColumn`; `Debug.getMemoryInfo` и работа с `ContentResolver` вынесены на `Dispatchers.IO`.
  - Настроен общий `ImageLoader` для Coil (`20%` RAM + `64 МБ` дискового кэша).
- **Адаптивность под узкие экраны (`2392×1080` и `1080×2400`):**
  - Исправлен вертикальный перенос бейджа статуса (`Скан`/`Пауза`/`Стоп`) в `FindingsFeedScreen`, убрано обрезание счётчика вкладок в `ModernFeedTabPill`, оверлей-бейджи переведены на `FlowRow` с `softWrap = false`.

---

### Новые функции ленты, карточки лота и радаров
- **Свайп-жесты и быстрые действия (`FindingsFeedScreen`):**
  - Свайп вправо — добавить/убрать из избранного, свайп влево — удалить с кнопкой «Отменить» в `Snackbar`.
  - Кнопка **«Прочитать все»** в верхней панели ленты находок.
- **Просмотр лота (`DealInspectorModal`):**
  - Галерея фотографий через `HorizontalPager` + полноэкранный просмотр `ZoomablePhotoGalleryDialog` с pinch-to-zoom, перетаскиванием и двойным тапом.
  - **История цен** по каждому объявлению (`PriceHistory`) с отображением динамики изменений.
  - Отображение имени продавца и кнопка **«Скрыть продавца»** для добавления в чёрный список.
- **Создание радаров через системное «Поделиться» и мульти-город:**
  - Добавлен `ACTION_SEND` (`text/plain`) `intent-filter` в `AndroidManifest.xml`: отправка ссылки из браузера или приложения OLX открывает редактор радара с автозаполнением параметров (`OlxUrlImporter`).
  - Поддержка выбора **нескольких городов в одном радаре** (`OlxCities.splitCities`, фильтрация UA/RU синонимов).

---

### Умный поиск и фильтрация
- **Инлайн-исключения в запросе:** поддержка `-минус-слов` прямо в поисковом запросе (`OlxParser.parseQuery`).
- **Умный поиск (`OlxParser.matchesWatchSmart`, `computeDealScore`):**
  - Умный анти-спам с защитой от отрицаний (например, «не копия»).
  - Строгое совпадение слов запроса, глобальный фильтр OLX Доставки, конвертация `USD`/`EUR` в `UAH` по настраиваемому курсу.
  - Расчёт рейтинга выгодности `dealScore`, многостраничное сканирование (`scanDepthPages` 1–3 стр.), отслеживание снижения цены (`price_drop` + учёт валют в `seen_items.last_currency`).
- **Чёрный список продавцов (`SellerBlacklistDao`):** скрывает объявления заблокированных продавцов в ленте и подавляет уведомления в `MonitorService`.
- **Дедупликация уведомлений:** сохранены `distinctBy { it.id }`, `GROUP_ALERT_CHILDREN` и окно защиты от дублей `recentAlertsCache` (с пропуском при реальном снижении цены).

---

### Уведомления, Telegram, фон и устойчивость к удалению
- **Пересылка в Telegram-бота (`TelegramNotifier`):** настройка `Bot Token`, `Chat ID`, порога `Deal Score` и тестовой отправки в настройках уведомлений.
- **Кнопка «В избранное» в push-уведомлении (`NotificationActionReceiver`):** быстрое сохранение прямо из шторки уведомлений.
- **Стабильность фона:** периодический watchdog на `WorkManager` (`MonitorWatchdogWorker`, каждые 15 мин с безопасным fallback на push для Android 14+), on-demand `Configuration.Provider` в `OlxRadarApp`, проверка и кнопка отключения ограничений батареи.
- **Сохранность файлов после удаления и шифрование бэкапа:**
  - `android:hasFragileUserData="true"`, правила автобэкапа (`backup_rules.xml`, `data_extraction_rules.xml`), автосохранение в `Documents/OLXRadar/` и SAF-папку (`PersistentStorageManager`), формат бэкапа v2 с избранным/находками/настройками (`RadarBackup`).
  - Опциональное **шифрование JSON-бэкапа паролем (AES-256-GCM + PBKDF2)** при экспорте и автоматический запрос пароля при импорте.
- **Расширенные настройки (`AppPreferences`, `StatusAiSettingsScreen`):** категории `Все`, `Дизайн`, `Поиск`, `Пуши`, `Нейросеть`, `Файлы`.

---

### Схема базы данных и релизная сборка
- Схема Room обновлена до **версии 11** (`MIGRATION_8_9`, `MIGRATION_9_10`, `MIGRATION_10_11`), экспортированы схемы `9.json`, `10.json`, `11.json`.
- Включены `isMinifyEnabled = true` и `isShrinkResources = true` для `release` с обновлёнными правилами `proguard-rules.pro`.
- Юнит-тесты: **66/66** пройдено (`MigrationTest`, `SearchAndBackupTest`, `OlxParserTest`, `AiFilterEngineTest`, `GroqVisionClientTest`, `FindingDaoTest`, `FindingsUiTest`).
