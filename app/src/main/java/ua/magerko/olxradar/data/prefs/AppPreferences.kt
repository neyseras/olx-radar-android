package ua.magerko.olxradar.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ua.magerko.olxradar.service.MonitorService

/**
 * Централизованное хранилище настроек приложения (UI, Поиск, Уведомления, Бэкапы).
 * Реактивно публикует изменения через StateFlow для мгновенного обновления UI без пересоздания экранов.
 */
object AppPreferences {

    private const val KEY_COMPACT_CARDS = "ui_compact_cards"
    private const val KEY_SHOW_MARKET_DIFF = "ui_show_market_diff"
    private const val KEY_REDUCE_ANIMATIONS = "ui_reduce_animations"
    private const val KEY_FEED_LIMIT = "ui_feed_limit"

    private const val KEY_SCAN_DEPTH_PAGES = "search_scan_depth_pages"
    private const val KEY_SMART_ANTI_SPAM = "search_smart_anti_spam"
    private const val KEY_TRACK_PRICE_DROPS = "search_track_price_drops"
    private const val KEY_MIN_PRICE_DROP_PCT = "search_min_price_drop_pct"
    private const val KEY_CONVERT_CURRENCY = "search_convert_currency"
    private const val KEY_USD_RATE = "search_usd_rate"
    private const val KEY_EUR_RATE = "search_eur_rate"
    private const val KEY_STRICT_QUERY_MATCH = "search_strict_query_match"
    private const val KEY_ONLY_DELIVERY_GLOBAL = "search_only_delivery_global"

    private const val KEY_NOTIF_SOUND = "notif_sound_enabled"
    private const val KEY_NOTIF_VIBRATION = "notif_vibration_enabled"
    private const val KEY_NOTIF_PHOTOS = "notif_show_photos"
    private const val KEY_NOTIF_GROUP = "notif_group_by_radar"
    private const val KEY_NOTIF_PRICE_DROPS = "notif_price_drops"

    private const val KEY_AUTO_BACKUP = "storage_auto_backup_public"
    private const val KEY_BACKUP_INCLUDE_FINDINGS = "storage_backup_include_findings"
    private const val KEY_BACKUP_INCLUDE_KEYS = "storage_backup_include_keys"
    private const val KEY_SAF_TREE_URI = "storage_saf_tree_uri"
    private const val KEY_LAST_BACKUP_AT = "storage_last_backup_at"
    private const val KEY_LAST_BACKUP_STATUS = "storage_last_backup_status"

    private const val KEY_TELEGRAM_ENABLED = "tg_enabled"
    private const val KEY_TELEGRAM_BOT_TOKEN = "tg_bot_token"
    private const val KEY_TELEGRAM_CHAT_ID = "tg_chat_id"
    private const val KEY_TELEGRAM_MIN_SCORE = "tg_min_score"
    private const val KEY_WATCHDOG_ENABLED = "bg_watchdog_enabled"

    data class Snapshot(
        val compactCards: Boolean = false,
        val showMarketDiff: Boolean = true,
        val reduceAnimations: Boolean = false,
        val feedLimit: Int = 150,
        val scanDepthPages: Int = 1,
        val smartAntiSpam: Boolean = true,
        val trackPriceDrops: Boolean = true,
        val minPriceDropPercent: Int = 5,
        val convertForeignCurrency: Boolean = true,
        val usdToUahRate: Float = 41.5f,
        val eurToUahRate: Float = 44.5f,
        val strictQueryWordMatch: Boolean = false,
        val onlyDeliveryGlobal: Boolean = false,
        val notifSoundEnabled: Boolean = true,
        val notifVibrationEnabled: Boolean = true,
        val notifShowPhotos: Boolean = true,
        val notifGroupByRadar: Boolean = true,
        val notifPriceDrops: Boolean = true,
        val autoBackupToPublicStorage: Boolean = true,
        val backupIncludeFindings: Boolean = true,
        val backupIncludeApiKeys: Boolean = false,
        val safBackupTreeUri: String? = null,
        val lastAutoBackupAt: Long = 0L,
        val lastAutoBackupStatus: String = "",
        val telegramEnabled: Boolean = false,
        val telegramBotToken: String = "",
        val telegramChatId: String = "",
        val telegramMinDealScore: Int = 0,
        val watchdogEnabled: Boolean = true
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    @Volatile
    private var initialized = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(MonitorService.PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                refresh(context)
                initialized = true
            }
        }
    }

    fun refresh(context: Context) {
        val p = prefs(context)
        _state.value = Snapshot(
            compactCards = p.getBoolean(KEY_COMPACT_CARDS, false),
            showMarketDiff = p.getBoolean(KEY_SHOW_MARKET_DIFF, true),
            reduceAnimations = p.getBoolean(KEY_REDUCE_ANIMATIONS, false),
            feedLimit = p.getInt(KEY_FEED_LIMIT, 150).coerceIn(50, 500),
            scanDepthPages = p.getInt(KEY_SCAN_DEPTH_PAGES, 1).coerceIn(1, 3),
            smartAntiSpam = p.getBoolean(KEY_SMART_ANTI_SPAM, true),
            trackPriceDrops = p.getBoolean(KEY_TRACK_PRICE_DROPS, true),
            minPriceDropPercent = p.getInt(KEY_MIN_PRICE_DROP_PCT, 5).coerceIn(1, 50),
            convertForeignCurrency = p.getBoolean(KEY_CONVERT_CURRENCY, true),
            usdToUahRate = p.getFloat(KEY_USD_RATE, 41.5f).coerceIn(10f, 200f),
            eurToUahRate = p.getFloat(KEY_EUR_RATE, 44.5f).coerceIn(10f, 200f),
            strictQueryWordMatch = p.getBoolean(KEY_STRICT_QUERY_MATCH, false),
            onlyDeliveryGlobal = p.getBoolean(KEY_ONLY_DELIVERY_GLOBAL, false),
            notifSoundEnabled = p.getBoolean(KEY_NOTIF_SOUND, true),
            notifVibrationEnabled = p.getBoolean(KEY_NOTIF_VIBRATION, true),
            notifShowPhotos = p.getBoolean(KEY_NOTIF_PHOTOS, true),
            notifGroupByRadar = p.getBoolean(KEY_NOTIF_GROUP, true),
            notifPriceDrops = p.getBoolean(KEY_NOTIF_PRICE_DROPS, true),
            autoBackupToPublicStorage = p.getBoolean(KEY_AUTO_BACKUP, true),
            backupIncludeFindings = p.getBoolean(KEY_BACKUP_INCLUDE_FINDINGS, true),
            backupIncludeApiKeys = p.getBoolean(KEY_BACKUP_INCLUDE_KEYS, false),
            safBackupTreeUri = p.getString(KEY_SAF_TREE_URI, null),
            lastAutoBackupAt = p.getLong(KEY_LAST_BACKUP_AT, 0L),
            lastAutoBackupStatus = p.getString(KEY_LAST_BACKUP_STATUS, "") ?: "",
            telegramEnabled = p.getBoolean(KEY_TELEGRAM_ENABLED, false),
            telegramBotToken = p.getString(KEY_TELEGRAM_BOT_TOKEN, "") ?: "",
            telegramChatId = p.getString(KEY_TELEGRAM_CHAT_ID, "") ?: "",
            telegramMinDealScore = p.getInt(KEY_TELEGRAM_MIN_SCORE, 0).coerceIn(0, 95),
            watchdogEnabled = p.getBoolean(KEY_WATCHDOG_ENABLED, true)
        )
    }

    fun update(context: Context, editBlock: SharedPreferences.Editor.() -> Unit) {
        val editor = prefs(context).edit()
        editor.editBlock()
        editor.apply()
        refresh(context)
    }

    fun setCompactCards(context: Context, value: Boolean) = update(context) { putBoolean(KEY_COMPACT_CARDS, value) }
    fun setShowMarketDiff(context: Context, value: Boolean) = update(context) { putBoolean(KEY_SHOW_MARKET_DIFF, value) }
    fun setReduceAnimations(context: Context, value: Boolean) = update(context) { putBoolean(KEY_REDUCE_ANIMATIONS, value) }
    fun setFeedLimit(context: Context, value: Int) = update(context) { putInt(KEY_FEED_LIMIT, value.coerceIn(50, 500)) }

    fun setScanDepthPages(context: Context, value: Int) = update(context) { putInt(KEY_SCAN_DEPTH_PAGES, value.coerceIn(1, 3)) }
    fun setSmartAntiSpam(context: Context, value: Boolean) = update(context) { putBoolean(KEY_SMART_ANTI_SPAM, value) }
    fun setTrackPriceDrops(context: Context, value: Boolean) = update(context) { putBoolean(KEY_TRACK_PRICE_DROPS, value) }
    fun setMinPriceDropPercent(context: Context, value: Int) = update(context) { putInt(KEY_MIN_PRICE_DROP_PCT, value.coerceIn(1, 50)) }
    fun setConvertForeignCurrency(context: Context, value: Boolean) = update(context) { putBoolean(KEY_CONVERT_CURRENCY, value) }
    fun setUsdRate(context: Context, value: Float) = update(context) { putFloat(KEY_USD_RATE, value.coerceIn(10f, 200f)) }
    fun setEurRate(context: Context, value: Float) = update(context) { putFloat(KEY_EUR_RATE, value.coerceIn(10f, 200f)) }
    fun setStrictQueryWordMatch(context: Context, value: Boolean) = update(context) { putBoolean(KEY_STRICT_QUERY_MATCH, value) }
    fun setOnlyDeliveryGlobal(context: Context, value: Boolean) = update(context) { putBoolean(KEY_ONLY_DELIVERY_GLOBAL, value) }

    fun setNotifSoundEnabled(context: Context, value: Boolean) = update(context) { putBoolean(KEY_NOTIF_SOUND, value) }
    fun setNotifVibrationEnabled(context: Context, value: Boolean) = update(context) { putBoolean(KEY_NOTIF_VIBRATION, value) }
    fun setNotifShowPhotos(context: Context, value: Boolean) = update(context) { putBoolean(KEY_NOTIF_PHOTOS, value) }
    fun setNotifGroupByRadar(context: Context, value: Boolean) = update(context) { putBoolean(KEY_NOTIF_GROUP, value) }
    fun setNotifPriceDrops(context: Context, value: Boolean) = update(context) { putBoolean(KEY_NOTIF_PRICE_DROPS, value) }

    fun setAutoBackupToPublicStorage(context: Context, value: Boolean) = update(context) { putBoolean(KEY_AUTO_BACKUP, value) }
    fun setBackupIncludeFindings(context: Context, value: Boolean) = update(context) { putBoolean(KEY_BACKUP_INCLUDE_FINDINGS, value) }
    fun setBackupIncludeApiKeys(context: Context, value: Boolean) = update(context) { putBoolean(KEY_BACKUP_INCLUDE_KEYS, value) }
    fun setSafBackupTreeUri(context: Context, uri: String?) = update(context) {
        if (uri.isNullOrBlank()) remove(KEY_SAF_TREE_URI) else putString(KEY_SAF_TREE_URI, uri)
    }

    fun recordBackupResult(context: Context, timestampMs: Long, status: String) = update(context) {
        putLong(KEY_LAST_BACKUP_AT, timestampMs)
        putString(KEY_LAST_BACKUP_STATUS, status)
    }

    fun setTelegramEnabled(context: Context, value: Boolean) = update(context) { putBoolean(KEY_TELEGRAM_ENABLED, value) }
    fun setTelegramBotToken(context: Context, value: String) = update(context) { putString(KEY_TELEGRAM_BOT_TOKEN, value.trim()) }
    fun setTelegramChatId(context: Context, value: String) = update(context) { putString(KEY_TELEGRAM_CHAT_ID, value.trim()) }
    fun setTelegramMinDealScore(context: Context, value: Int) = update(context) { putInt(KEY_TELEGRAM_MIN_SCORE, value.coerceIn(0, 95)) }
    fun setWatchdogEnabled(context: Context, value: Boolean) = update(context) { putBoolean(KEY_WATCHDOG_ENABLED, value) }
}
