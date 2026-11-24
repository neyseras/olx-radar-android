package ua.magerko.olxradar.data.backup

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import ua.magerko.olxradar.ai.AiPreferences
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.PriceHistory
import ua.magerko.olxradar.data.model.SellerBlacklist
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.data.prefs.AppPreferences
import ua.magerko.olxradar.service.MonitorService
import ua.magerko.olxradar.ui.compose.ThemeMode
import ua.magerko.olxradar.ui.compose.ThemePreferences

/**
 * Единый экспорт и импорт полного бэкапа:
 * - Радары (Watches)
 * - Настройки (Settings: AI, Telegram, интерфейс, фильтры, тихие часы)
 * - Избранное и находки (Findings)
 * - Чёрный список продавцов (SellerBlacklist)
 * - История динамики цен (PriceHistory)
 *
 * Поддерживает форматы:
 * - Единый JSON (.json): olx_radar_backup.json
 * - JSON Lines (.jsonl): olx_radar_backup.jsonl (каждая строка — отдельный объект)
 * - Совместимость с предыдущими v1 (olx-radar-list.config) и v2 (olx_radar_autobackup.json)
 * - Опциональное шифрование AES-256-GCM по паролю
 */
object RadarBackup {

    private const val TAG = "RadarBackup"
    const val UNIFIED_BACKUP_FILE_NAME = "olx_radar_backup.json"
    const val UNIFIED_BACKUP_JSONL_FILE_NAME = "olx_radar_backup.jsonl"
    const val FULL_BACKUP_FILE_NAME = "olx_radar_backup.json"
    const val LEGACY_FULL_BACKUP_FILE_NAME = "olx_radar_autobackup.json"
    const val DEFAULT_FILE_NAME = "olx-radar-list.config"

    private const val FORMAT_V1 = 1
    private const val FORMAT_V2 = 2
    private const val FORMAT_V3 = 3

    data class FullBackupData(
        val format: Int,
        val exportedAt: Long,
        val watches: List<Watch>,
        val findings: List<Finding>,
        val settingsJson: JSONObject?,
        val blockedSellers: List<SellerBlacklist> = emptyList(),
        val priceHistory: List<PriceHistory> = emptyList()
    )

    /**
     * Экспорт только радаров в JSON (legacy формат v1).
     */
    fun toJson(watches: List<Watch>, exportedAt: Long = System.currentTimeMillis()): String {
        val root = JSONObject()
        root.put("app", "OLX Radar")
        root.put("format", FORMAT_V1)
        root.put("exported_at", exportedAt)
        root.put("radars", watchesToJsonArray(watches))
        return root.toString(2)
    }

    /**
     * Создаёт единый JSON файл полного бэкапа (формат v3).
     */
    fun toFullBackupJson(
        context: Context,
        watches: List<Watch>,
        findings: List<Finding> = emptyList(),
        blockedSellers: List<SellerBlacklist> = emptyList(),
        priceHistory: List<PriceHistory> = emptyList(),
        includeFindings: Boolean = true,
        includeApiKeys: Boolean = false,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject()
        root.put("app", "OLX Radar")
        root.put("format", FORMAT_V3)
        root.put("exported_at", exportedAt)
        root.put("version_name", "2611081")
        root.put("radars", watchesToJsonArray(watches))
        root.put("settings", buildSettingsJsonObject(context, includeApiKeys))

        if (blockedSellers.isNotEmpty()) {
            root.put("blocked_sellers", blockedSellersToJsonArray(blockedSellers))
        }

        if (includeFindings && findings.isNotEmpty()) {
            root.put("findings", findingsToJsonArray(findings))
        }

        if (priceHistory.isNotEmpty()) {
            root.put("price_history", priceHistoryToJsonArray(priceHistory))
        }

        return root.toString(2)
    }

    /**
     * Создаёт единый файл бэкапа в формате JSON Lines (.jsonl).
     * Каждая строка — самодостаточный JSON-объект с полем "type".
     */
    fun toFullBackupJsonl(
        context: Context,
        watches: List<Watch>,
        findings: List<Finding> = emptyList(),
        blockedSellers: List<SellerBlacklist> = emptyList(),
        priceHistory: List<PriceHistory> = emptyList(),
        includeFindings: Boolean = true,
        includeApiKeys: Boolean = false,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val sb = StringBuilder()

        // 1. Meta header
        val meta = JSONObject().apply {
            put("type", "meta")
            put("app", "OLX Radar")
            put("format", FORMAT_V3)
            put("exported_at", exportedAt)
            put("version_name", "2611081")
        }
        sb.append(meta.toString()).append("\n")

        // 2. Settings
        val settingsLine = JSONObject().apply {
            put("type", "settings")
            put("data", buildSettingsJsonObject(context, includeApiKeys))
        }
        sb.append(settingsLine.toString()).append("\n")

        // 3. Radars (Watches)
        for (w in watches) {
            val o = watchToJsonObject(w)
            o.put("type", "radar")
            sb.append(o.toString()).append("\n")
        }

        // 4. Blocked Sellers
        for (b in blockedSellers) {
            val o = blockedSellerToJsonObject(b)
            o.put("type", "blocked_seller")
            sb.append(o.toString()).append("\n")
        }

        // 5. Findings
        if (includeFindings && findings.isNotEmpty()) {
            val prioritized = findings.sortedWith(
                compareByDescending<Finding> { it.isFavorite }
                    .thenByDescending { it.foundAt }
            ).take(300)
            for (f in prioritized) {
                val o = findingToJsonObject(f)
                o.put("type", "finding")
                sb.append(o.toString()).append("\n")
            }
        }

        // 6. Price History
        for (ph in priceHistory.take(500)) {
            val o = priceHistoryToJsonObject(ph)
            o.put("type", "price_history")
            sb.append(o.toString()).append("\n")
        }

        return sb.toString()
    }

    fun buildSettingsJsonObject(context: Context, includeApiKeys: Boolean): JSONObject {
        val prefs = context.applicationContext.getSharedPreferences(MonitorService.PREFS_NAME, Context.MODE_PRIVATE)
        val appSnap = AppPreferences.state.value
        val aiPrefs = AiPreferences(context)
        val theme = ThemePreferences.get(context)

        return JSONObject().apply {
            put("theme_mode", theme.name)
            put("poll_interval_sec", prefs.getInt(MonitorService.KEY_INTERVAL, MonitorService.DEFAULT_INTERVAL_SEC))
            put("quiet_hours_enabled", prefs.getBoolean(MonitorService.KEY_QUIET_HOURS_ENABLED, false))
            put("quiet_hours_from_hour", prefs.getInt(MonitorService.KEY_QUIET_HOURS_FROM_HOUR, 23))
            put("quiet_hours_from_min", prefs.getInt(MonitorService.KEY_QUIET_HOURS_FROM_MIN, 0))
            put("quiet_hours_to_hour", prefs.getInt(MonitorService.KEY_QUIET_HOURS_TO_HOUR, 7))
            put("quiet_hours_to_min", prefs.getInt(MonitorService.KEY_QUIET_HOURS_TO_MIN, 0))

            put("compact_cards", appSnap.compactCards)
            put("show_market_diff", appSnap.showMarketDiff)
            put("reduce_animations", appSnap.reduceAnimations)
            put("feed_limit", appSnap.feedLimit)
            put("scan_depth_pages", appSnap.scanDepthPages)
            put("smart_anti_spam", appSnap.smartAntiSpam)
            put("track_price_drops", appSnap.trackPriceDrops)
            put("min_price_drop_percent", appSnap.minPriceDropPercent)
            put("convert_foreign_currency", appSnap.convertForeignCurrency)
            put("usd_rate", appSnap.usdToUahRate.toDouble())
            put("eur_rate", appSnap.eurToUahRate.toDouble())
            put("strict_query_match", appSnap.strictQueryWordMatch)
            put("only_delivery_global", appSnap.onlyDeliveryGlobal)

            put("notif_sound", appSnap.notifSoundEnabled)
            put("notif_vibration", appSnap.notifVibrationEnabled)
            put("notif_photos", appSnap.notifShowPhotos)
            put("notif_group", appSnap.notifGroupByRadar)
            put("notif_price_drops", appSnap.notifPriceDrops)

            put("auto_backup", appSnap.autoBackupToPublicStorage)
            put("backup_include_findings", appSnap.backupIncludeFindings)
            put("backup_include_keys", appSnap.backupIncludeApiKeys)

            put("ai_enabled", aiPrefs.isAiEnabled)
            put("ai_provider", aiPrefs.provider)
            put("ai_base_url", aiPrefs.baseUrl)
            put("ai_model", aiPrefs.modelId)
            put("ai_confidence", aiPrefs.confidenceThreshold)
            put("ai_max_photos", aiPrefs.maxPhotos)
            put("ai_check_suspicious", aiPrefs.isCheckSuspicious)
            put("ai_strict_mode", aiPrefs.isStrictMode)
            put("ai_manual_review", aiPrefs.isManualReviewMode)
            put("ai_notify_rejected", aiPrefs.isNotifyRejected)
            put("ai_max_concurrent", aiPrefs.maxConcurrentRequests)
            put("ai_timeout_sec", aiPrefs.timeoutSeconds)

            put("backup_ai_enabled", aiPrefs.isBackupAiEnabled)
            put("backup_provider", aiPrefs.backupProvider)
            put("backup_base_url", aiPrefs.backupBaseUrl)
            put("backup_model", aiPrefs.backupModelId)

            put("tg_enabled", appSnap.telegramEnabled)
            put("tg_chat_id", appSnap.telegramChatId)
            put("tg_min_score", appSnap.telegramMinDealScore)
            put("bg_watchdog_enabled", appSnap.watchdogEnabled)

            if (includeApiKeys) {
                put("ai_api_key", aiPrefs.apiKey)
                put("backup_api_key", aiPrefs.backupApiKey)
                if (appSnap.telegramBotToken.isNotBlank()) {
                    put("tg_bot_token", appSnap.telegramBotToken)
                }
            }
        }
    }

    private fun watchToJsonObject(w: Watch): JSONObject = JSONObject().apply {
        put("source_id", w.id)
        put("title", w.title)
        put("query", w.query)
        put("price_from", w.priceFrom ?: JSONObject.NULL)
        put("price_to", w.priceTo ?: JSONObject.NULL)
        put("currency", w.priceCurrency)
        put("city_name", w.cityName ?: "")
        put("city_id", w.cityId ?: JSONObject.NULL)
        put("category_id", w.categoryId ?: JSONObject.NULL)
        put("category_preset", w.categoryPreset)
        put("owner_type", w.ownerType ?: "any")
        put("state", w.state ?: "any")
        put("stop_words", w.stopWords)
        put("must_words", w.mustWords)
        put("must_words_mode", w.mustWordsMode)
        put("publication_age", w.publicationAge)
        put("condition_target", w.conditionTarget)
        put("condition_threshold", w.conditionThreshold)
        put("notify_mode", w.notifyMode)
        put("include_no_price", w.includeNoPrice)
        put("interval_sec_override", w.intervalSecOverride ?: JSONObject.NULL)
        put("is_active", w.isActive)
    }

    private fun watchesToJsonArray(watches: List<Watch>): JSONArray {
        val arr = JSONArray()
        watches.forEach { arr.put(watchToJsonObject(it)) }
        return arr
    }

    private fun findingToJsonObject(f: Finding): JSONObject = JSONObject().apply {
        put("watch_id", f.watchId)
        put("listing_id", f.listingId)
        put("title", f.title)
        put("description", f.description)
        put("price", f.price ?: JSONObject.NULL)
        put("currency", f.currency ?: JSONObject.NULL)
        put("city_name", f.cityName)
        put("url", f.url)
        put("photo_url", f.photoUrl ?: JSONObject.NULL)
        put("reason", f.reason)
        put("created_at", f.createdAt)
        put("found_at", f.foundAt)
        put("is_read", f.isRead)
        put("condition_score", f.conditionScore ?: JSONObject.NULL)
        put("ai_score", f.aiScore ?: JSONObject.NULL)
        put("ai_status", f.aiStatus ?: JSONObject.NULL)
        put("ai_product", f.aiProduct ?: JSONObject.NULL)
        put("ai_model", f.aiModel ?: JSONObject.NULL)
        put("ai_photo_match", f.aiPhotoMatch ?: JSONObject.NULL)
        put("ai_reasons", f.aiReasons ?: JSONObject.NULL)
        put("ai_reject_reason", f.aiRejectReason ?: JSONObject.NULL)
        put("ai_details_json", f.aiDetailsJson ?: JSONObject.NULL)
        put("is_delivery", f.isDelivery)
        put("is_favorite", f.isFavorite)
        put("old_price", f.oldPrice ?: JSONObject.NULL)
        put("deal_score", f.dealScore ?: JSONObject.NULL)
        put("photo_urls_json", f.photoUrlsJson ?: JSONObject.NULL)
    }

    private fun findingsToJsonArray(findings: List<Finding>): JSONArray {
        val arr = JSONArray()
        val prioritized = findings.sortedWith(
            compareByDescending<Finding> { it.isFavorite }
                .thenByDescending { it.foundAt }
        ).take(300)
        for (f in prioritized) {
            arr.put(findingToJsonObject(f))
        }
        return arr
    }

    private fun blockedSellerToJsonObject(b: SellerBlacklist): JSONObject = JSONObject().apply {
        put("seller_key", b.sellerKey)
        put("display_name", b.displayName)
        put("added_at", b.addedAt)
    }

    private fun blockedSellersToJsonArray(list: List<SellerBlacklist>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(blockedSellerToJsonObject(it)) }
        return arr
    }

    private fun priceHistoryToJsonObject(ph: PriceHistory): JSONObject = JSONObject().apply {
        put("watch_id", ph.watchId)
        put("listing_id", ph.listingId)
        put("price", ph.price)
        put("currency", ph.currency ?: JSONObject.NULL)
        put("recorded_at", ph.recordedAt)
    }

    private fun priceHistoryToJsonArray(list: List<PriceHistory>): JSONArray {
        val arr = JSONArray()
        list.take(500).forEach { arr.put(priceHistoryToJsonObject(it)) }
        return arr
    }

    fun fromJson(json: String): List<Watch> {
        return parseFullBackup(json).watches
    }

    /**
     * Универсальный парсер:
     * - Автоматически распознаёт .jsonl (JSON Lines)
     * - Распознаёт JSON v1, v2, v3
     * - Распознаёт сырой массив радаров [{...}]
     */
    fun parseFullBackup(rawText: String): FullBackupData {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "Файл бэкапа пуст" }

        // Проверка на JSON Lines (.jsonl)
        val lines = trimmed.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val isJsonl = lines.size > 1 && lines.all { it.startsWith("{") && it.endsWith("}") } &&
                lines.any { it.contains("\"type\"") }

        if (isJsonl) {
            return parseJsonl(lines)
        }

        // Если это массив радаров [{...}]
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            val watches = parseWatchesArray(arr)
            return FullBackupData(
                format = FORMAT_V1,
                exportedAt = System.currentTimeMillis(),
                watches = watches,
                findings = emptyList(),
                settingsJson = null
            )
        }

        // Стандартный JSON объект
        val root = JSONObject(trimmed)
        val format = root.optInt("format", FORMAT_V1)
        val exportedAt = root.optLong("exported_at", 0L)
        val watches = parseWatchesArray(root.optJSONArray("radars"))
        val findings = parseFindingsArray(root.optJSONArray("findings"))
        val blockedSellers = parseBlockedSellersArray(root.optJSONArray("blocked_sellers"))
        val priceHistory = parsePriceHistoryArray(root.optJSONArray("price_history"))
        val settings = root.optJSONObject("settings")

        return FullBackupData(
            format = format,
            exportedAt = exportedAt,
            watches = watches.filter { it.query.isNotBlank() },
            findings = findings.filter { it.listingId > 0L && it.title.isNotBlank() },
            settingsJson = settings,
            blockedSellers = blockedSellers,
            priceHistory = priceHistory
        )
    }

    private fun parseJsonl(lines: List<String>): FullBackupData {
        var format = FORMAT_V3
        var exportedAt = System.currentTimeMillis()
        var settingsJson: JSONObject? = null
        val watches = mutableListOf<Watch>()
        val findings = mutableListOf<Finding>()
        val blockedSellers = mutableListOf<SellerBlacklist>()
        val priceHistory = mutableListOf<PriceHistory>()

        for ((index, line) in lines.withIndex()) {
            try {
                val obj = JSONObject(line)
                val type = obj.optString("type", "").lowercase()
                when (type) {
                    "meta" -> {
                        format = obj.optInt("format", FORMAT_V3)
                        exportedAt = obj.optLong("exported_at", exportedAt)
                    }
                    "settings" -> {
                        settingsJson = obj.optJSONObject("data") ?: obj
                    }
                    "radar", "watch" -> {
                        parseWatchObject(obj)?.let { watches += it }
                    }
                    "finding" -> {
                        parseFindingObject(obj)?.let { findings += it }
                    }
                    "blocked_seller", "seller_blacklist" -> {
                        parseBlockedSellerObject(obj)?.let { blockedSellers += it }
                    }
                    "price_history" -> {
                        parsePriceHistoryObject(obj)?.let { priceHistory += it }
                    }
                    else -> {
                        if (obj.has("query")) {
                            parseWatchObject(obj)?.let { watches += it }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Пропуск повреждённой строки JSONL #${index + 1}: ${e.message}")
            }
        }

        return FullBackupData(
            format = format,
            exportedAt = exportedAt,
            watches = watches.filter { it.query.isNotBlank() },
            findings = findings.filter { it.listingId > 0L && it.title.isNotBlank() },
            settingsJson = settingsJson,
            blockedSellers = blockedSellers,
            priceHistory = priceHistory
        )
    }

    fun parseWatchObject(o: JSONObject): Watch? {
        return try {
            val sourceId = o.optLong("source_id", 0L)
            Watch(
                id = sourceId,
                title = o.optString("title", "").ifBlank { o.optString("query", "Radar") },
                query = o.optString("query", ""),
                priceFrom = o.optIntOrNullOrNull("price_from"),
                priceTo = o.optIntOrNullOrNull("price_to"),
                priceCurrency = o.optString("currency", "UAH").ifBlank { "UAH" },
                cityName = o.optString("city_name", "").takeIf { it.isNotBlank() },
                cityId = o.optIntOrNullOrNull("city_id"),
                categoryId = o.optIntOrNullOrNull("category_id"),
                categoryPreset = o.optString("category_preset", "any").ifBlank { "any" },
                ownerType = o.optString("owner_type", "any").takeIf { it.isNotBlank() && it != "any" },
                state = o.optString("state", "any").takeIf { it.isNotBlank() && it != "any" },
                stopWords = o.optString("stop_words", ""),
                mustWords = o.optString("must_words", ""),
                mustWordsMode = o.optString("must_words_mode", "any").ifBlank { "any" },
                publicationAge = o.optString("publication_age", "any").ifBlank { "any" },
                conditionTarget = o.optString("condition_target", "any").ifBlank { "any" },
                conditionThreshold = o.optInt("condition_threshold", 0).coerceIn(0, 100),
                notifyMode = o.optString("notify_mode", "new_pushup").ifBlank { "new_pushup" },
                includeNoPrice = o.optBoolean("include_no_price", true),
                intervalSecOverride = o.optIntOrNullOrNull("interval_sec_override"),
                isActive = o.optBoolean("is_active", true),
                createdAt = System.currentTimeMillis(),
                initialized = false
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseWatchesArray(arr: JSONArray?): List<Watch> {
        if (arr == null) return emptyList()
        val list = mutableListOf<Watch>()
        for (i in 0 until arr.length()) {
            try {
                parseWatchObject(arr.getJSONObject(i))?.let { list += it }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed radar #${i + 1}: ${e.message}")
            }
        }
        return list
    }

    fun parseFindingObject(o: JSONObject): Finding? {
        return try {
            Finding(
                id = 0L,
                watchId = o.optLong("watch_id", 0L),
                listingId = o.optLong("listing_id", 0L),
                title = o.optString("title", ""),
                description = o.optString("description", ""),
                price = o.optIntOrNullOrNull("price"),
                currency = o.optStringOrNull("currency"),
                cityName = o.optString("city_name", ""),
                url = o.optString("url", ""),
                photoUrl = o.optStringOrNull("photo_url"),
                reason = o.optString("reason", "new"),
                createdAt = o.optLong("created_at", System.currentTimeMillis()),
                foundAt = o.optLong("found_at", System.currentTimeMillis()),
                isRead = o.optBoolean("is_read", false),
                conditionScore = o.optIntOrNullOrNull("condition_score"),
                aiScore = o.optIntOrNullOrNull("ai_score"),
                aiStatus = o.optStringOrNull("ai_status"),
                aiProduct = o.optStringOrNull("ai_product"),
                aiModel = o.optStringOrNull("ai_model"),
                aiPhotoMatch = if (o.has("ai_photo_match") && !o.isNull("ai_photo_match")) o.optBoolean("ai_photo_match") else null,
                aiReasons = o.optStringOrNull("ai_reasons"),
                aiRejectReason = o.optStringOrNull("ai_reject_reason"),
                aiDetailsJson = o.optStringOrNull("ai_details_json"),
                isDelivery = o.optBoolean("is_delivery", false),
                isFavorite = o.optBoolean("is_favorite", false),
                oldPrice = o.optIntOrNullOrNull("old_price"),
                dealScore = o.optIntOrNullOrNull("deal_score"),
                photoUrlsJson = o.optStringOrNull("photo_urls_json")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFindingsArray(fArr: JSONArray?): List<Finding> {
        if (fArr == null) return emptyList()
        val list = mutableListOf<Finding>()
        for (i in 0 until fArr.length()) {
            try {
                parseFindingObject(fArr.getJSONObject(i))?.let { list += it }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed finding #${i + 1}: ${e.message}")
            }
        }
        return list
    }

    fun parseBlockedSellerObject(o: JSONObject): SellerBlacklist? {
        val key = o.optString("seller_key", "").trim()
        if (key.isBlank()) return null
        val name = o.optString("display_name", key)
        val addedAt = o.optLong("added_at", System.currentTimeMillis())
        return SellerBlacklist(sellerKey = key, displayName = name, addedAt = addedAt)
    }

    private fun parseBlockedSellersArray(arr: JSONArray?): List<SellerBlacklist> {
        if (arr == null) return emptyList()
        val list = mutableListOf<SellerBlacklist>()
        for (i in 0 until arr.length()) {
            try {
                parseBlockedSellerObject(arr.getJSONObject(i))?.let { list += it }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed blocked seller #${i + 1}: ${e.message}")
            }
        }
        return list
    }

    fun parsePriceHistoryObject(o: JSONObject): PriceHistory? {
        val listingId = o.optLong("listing_id", 0L)
        if (listingId <= 0L) return null
        val price = o.optInt("price", 0)
        val watchId = o.optLong("watch_id", 0L)
        val currency = o.optStringOrNull("currency")
        val recordedAt = o.optLong("recorded_at", System.currentTimeMillis())
        return PriceHistory(
            id = 0L,
            watchId = watchId,
            listingId = listingId,
            price = price,
            currency = currency,
            recordedAt = recordedAt
        )
    }

    private fun parsePriceHistoryArray(arr: JSONArray?): List<PriceHistory> {
        if (arr == null) return emptyList()
        val list = mutableListOf<PriceHistory>()
        for (i in 0 until arr.length()) {
            try {
                parsePriceHistoryObject(arr.getJSONObject(i))?.let { list += it }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed price history #${i + 1}: ${e.message}")
            }
        }
        return list
    }

    fun applySettingsFromJson(context: Context, s: JSONObject) {
        try {
            if (s.has("theme_mode")) {
                val mode = ThemeMode.fromId(s.optString("theme_mode"))
                val prefs = context.applicationContext.getSharedPreferences(MonitorService.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString("app_theme_mode", mode.name).apply()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    ua.magerko.olxradar.ui.compose.applyThemeMode(mode)
                }
            }
            val prefs = context.applicationContext.getSharedPreferences(MonitorService.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                if (s.has("poll_interval_sec")) putInt(MonitorService.KEY_INTERVAL, s.optInt("poll_interval_sec", MonitorService.DEFAULT_INTERVAL_SEC))
                if (s.has("quiet_hours_enabled")) putBoolean(MonitorService.KEY_QUIET_HOURS_ENABLED, s.optBoolean("quiet_hours_enabled"))
                if (s.has("quiet_hours_from_hour")) putInt(MonitorService.KEY_QUIET_HOURS_FROM_HOUR, s.optInt("quiet_hours_from_hour"))
                if (s.has("quiet_hours_from_min")) putInt(MonitorService.KEY_QUIET_HOURS_FROM_MIN, s.optInt("quiet_hours_from_min"))
                if (s.has("quiet_hours_to_hour")) putInt(MonitorService.KEY_QUIET_HOURS_TO_HOUR, s.optInt("quiet_hours_to_hour"))
                if (s.has("quiet_hours_to_min")) putInt(MonitorService.KEY_QUIET_HOURS_TO_MIN, s.optInt("quiet_hours_to_min"))
            }.apply()

            AppPreferences.update(context) {
                if (s.has("compact_cards")) putBoolean("ui_compact_cards", s.optBoolean("compact_cards"))
                if (s.has("show_market_diff")) putBoolean("ui_show_market_diff", s.optBoolean("show_market_diff"))
                if (s.has("reduce_animations")) putBoolean("ui_reduce_animations", s.optBoolean("reduce_animations"))
                if (s.has("feed_limit")) putInt("ui_feed_limit", s.optInt("feed_limit", 150))
                if (s.has("scan_depth_pages")) putInt("search_scan_depth_pages", s.optInt("scan_depth_pages", 1))
                if (s.has("smart_anti_spam")) putBoolean("search_smart_anti_spam", s.optBoolean("smart_anti_spam"))
                if (s.has("track_price_drops")) putBoolean("search_track_price_drops", s.optBoolean("track_price_drops"))
                if (s.has("min_price_drop_percent")) putInt("search_min_price_drop_pct", s.optInt("min_price_drop_percent", 5))
                if (s.has("convert_foreign_currency")) putBoolean("search_convert_currency", s.optBoolean("convert_foreign_currency"))
                if (s.has("usd_rate")) putFloat("search_usd_rate", s.optDouble("usd_rate", 41.5).toFloat())
                if (s.has("eur_rate")) putFloat("search_eur_rate", s.optDouble("eur_rate", 44.5).toFloat())
                if (s.has("strict_query_match")) putBoolean("search_strict_query_match", s.optBoolean("strict_query_match"))
                if (s.has("only_delivery_global")) putBoolean("search_only_delivery_global", s.optBoolean("only_delivery_global"))
                if (s.has("notif_sound")) putBoolean("notif_sound_enabled", s.optBoolean("notif_sound"))
                if (s.has("notif_vibration")) putBoolean("notif_vibration_enabled", s.optBoolean("notif_vibration"))
                if (s.has("notif_photos")) putBoolean("notif_show_photos", s.optBoolean("notif_photos"))
                if (s.has("notif_group")) putBoolean("notif_group_by_radar", s.optBoolean("notif_group"))
                if (s.has("notif_price_drops")) putBoolean("notif_price_drops", s.optBoolean("notif_price_drops"))
                if (s.has("auto_backup")) putBoolean("storage_auto_backup_public", s.optBoolean("auto_backup"))
                if (s.has("backup_include_findings")) putBoolean("storage_backup_include_findings", s.optBoolean("backup_include_findings"))
                if (s.has("backup_include_keys")) putBoolean("storage_backup_include_keys", s.optBoolean("backup_include_keys"))
                if (s.has("tg_enabled")) putBoolean("tg_enabled", s.optBoolean("tg_enabled"))
                if (s.has("tg_chat_id")) putString("tg_chat_id", s.optString("tg_chat_id", ""))
                if (s.has("tg_min_score")) putInt("tg_min_score", s.optInt("tg_min_score", 0))
                if (s.has("tg_bot_token") && s.optString("tg_bot_token").isNotBlank()) {
                    putString("tg_bot_token", s.optString("tg_bot_token"))
                }
                if (s.has("bg_watchdog_enabled")) putBoolean("bg_watchdog_enabled", s.optBoolean("bg_watchdog_enabled", true))
            }

            val aiPrefs = AiPreferences(context)
            if (s.has("ai_enabled")) aiPrefs.isAiEnabled = s.optBoolean("ai_enabled")
            if (s.has("ai_provider")) aiPrefs.provider = s.optString("ai_provider", aiPrefs.provider)
            if (s.has("ai_base_url")) aiPrefs.baseUrl = s.optString("ai_base_url", aiPrefs.baseUrl)
            if (s.has("ai_model")) aiPrefs.modelId = s.optString("ai_model", aiPrefs.modelId)
            if (s.has("ai_confidence")) aiPrefs.confidenceThreshold = s.optInt("ai_confidence", 75)
            if (s.has("ai_max_photos")) aiPrefs.maxPhotos = s.optInt("ai_max_photos", 1)
            if (s.has("ai_check_suspicious")) aiPrefs.isCheckSuspicious = s.optBoolean("ai_check_suspicious", true)
            if (s.has("ai_strict_mode")) aiPrefs.isStrictMode = s.optBoolean("ai_strict_mode", false)
            if (s.has("ai_manual_review")) aiPrefs.isManualReviewMode = s.optBoolean("ai_manual_review", true)
            if (s.has("ai_notify_rejected")) aiPrefs.isNotifyRejected = s.optBoolean("ai_notify_rejected", false)
            if (s.has("ai_max_concurrent")) aiPrefs.maxConcurrentRequests = s.optInt("ai_max_concurrent", 1)
            if (s.has("ai_timeout_sec")) aiPrefs.timeoutSeconds = s.optInt("ai_timeout_sec", 25)
            if (s.has("backup_ai_enabled")) aiPrefs.isBackupAiEnabled = s.optBoolean("backup_ai_enabled", false)
            if (s.has("backup_provider")) aiPrefs.backupProvider = s.optString("backup_provider", aiPrefs.backupProvider)
            if (s.has("backup_base_url")) aiPrefs.backupBaseUrl = s.optString("backup_base_url", aiPrefs.backupBaseUrl)
            if (s.has("backup_model")) aiPrefs.backupModelId = s.optString("backup_model", aiPrefs.backupModelId)
            if (s.has("ai_api_key") && s.optString("ai_api_key").isNotBlank()) {
                aiPrefs.apiKey = s.optString("ai_api_key")
            }
            if (s.has("backup_api_key") && s.optString("backup_api_key").isNotBlank()) {
                aiPrefs.backupApiKey = s.optString("backup_api_key")
            }
        } catch (e: Exception) {
            Log.w(TAG, "applySettingsFromJson error: ${e.message}")
        }
    }

    private fun JSONObject.optIntOrNullOrNull(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return optInt(name)
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private const val ENCRYPTED_FORMAT_KEY = "olx_radar_encrypted_v1"

    fun isEncryptedBackup(rawText: String): Boolean {
        return try {
            JSONObject(rawText).optBoolean(ENCRYPTED_FORMAT_KEY, false)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Шифрует JSON/JSONL бэкапа по паролю (PBKDF2WithHmacSHA256 + AES-256-GCM).
     */
    fun encryptBackupJson(plainText: String, password: String): String {
        require(password.isNotEmpty()) { "Пароль не может быть пустым" }
        val rng = SecureRandom()
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val key = deriveAesKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return JSONObject().apply {
            put("app", "OLX Radar")
            put(ENCRYPTED_FORMAT_KEY, true)
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterations", 65536)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("payload", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        }.toString(2)
    }

    /**
     * Расшифровывает защищённый паролем бэкап.
     */
    fun decryptBackupJson(encryptedJson: String, password: String): String {
        val root = JSONObject(encryptedJson)
        if (!root.optBoolean(ENCRYPTED_FORMAT_KEY, false)) return encryptedJson
        val salt = Base64.decode(root.getString("salt"), Base64.DEFAULT)
        val iv = Base64.decode(root.getString("iv"), Base64.DEFAULT)
        val payload = Base64.decode(root.getString("payload"), Base64.DEFAULT)
        val iterations = root.optInt("iterations", 65536).coerceIn(10000, 300000)

        val key = deriveAesKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(payload)
        return String(plainBytes, Charsets.UTF_8)
    }

    private fun deriveAesKey(password: String, salt: ByteArray, iterations: Int = 65536): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val encoded = factory.generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }
}
