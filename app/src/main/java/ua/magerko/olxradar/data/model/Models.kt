package ua.magerko.olxradar.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watches"
)
data class Watch(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val query: String,
    @ColumnInfo(name = "price_from")
    val priceFrom: Int? = null,
    @ColumnInfo(name = "price_to")
    val priceTo: Int? = null,
    @ColumnInfo(name = "city_id")
    val cityId: Int? = null,
    @ColumnInfo(name = "city_name")
    val cityName: String? = null,
    @ColumnInfo(name = "category_id")
    val categoryId: Int? = null,
    @ColumnInfo(name = "owner_type")
    val ownerType: String? = null, // "private", "business", or null
    val state: String? = null,     // "new", "used", or null
    @ColumnInfo(name = "stop_words")
    val stopWords: String = "",    // comma separated
    @ColumnInfo(name = "notify_mode")
    val notifyMode: String = "new", // "new" or "new_pushup"
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    // CONTRACT additions
    val initialized: Boolean = false,
    @ColumnInfo(name = "last_success_at")
    val lastSuccessAt: Long? = null,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "last_http_code")
    val lastHttpCode: Int? = null,
    @ColumnInfo(name = "include_no_price")
    val includeNoPrice: Boolean = false,
    @ColumnInfo(name = "price_currency", defaultValue = "UAH")
    val priceCurrency: String = "UAH",
    @ColumnInfo(name = "must_words")
    val mustWords: String = "",
    @ColumnInfo(name = "must_words_mode", defaultValue = "any")
    val mustWordsMode: String = "any",
    @ColumnInfo(name = "snoozed_until")
    val snoozedUntil: Long? = null,
    @ColumnInfo(name = "interval_sec_override")
    val intervalSecOverride: Int? = null,

    // Категория: null = все, 38 = компьютеры, 458 = комплектующие ПК, 44 = телефоны
    @ColumnInfo(name = "category_preset", defaultValue = "any")
    val categoryPreset: String = "any",

    // Сенсор состояния: "any" / "working" / "broken"
    @ColumnInfo(name = "condition_target", defaultValue = "any")
    val conditionTarget: String = "any",

    // Строгость сенсора состояния 0..100 (0 = выключен)
    @ColumnInfo(name = "condition_threshold", defaultValue = "0")
    val conditionThreshold: Int = 0,

    // Свежесть (возраст) публикации объявления:
    // "any" - любое время
    // "fresh" - только свежие (до 24 часов)
    // "recent" - недавние (до 3 дней)
    // "week" - за неделю (до 7 дней)
    // "old" - старые / «висяки» (старше 7 дней)
    @ColumnInfo(name = "publication_age", defaultValue = "any")
    val publicationAge: String = "any"
)

@Entity(
    tableName = "findings",
    indices = [
        Index(value = ["found_at"], orders = [Index.Order.DESC], name = "idx_findings_found_at"),
        Index(value = ["watch_id", "listing_id"], name = "idx_findings_watch_listing"),
        Index(value = ["watch_id"], name = "idx_findings_watch_id")
    ]
)
data class Finding(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "watch_id")
    val watchId: Long,
    @ColumnInfo(name = "listing_id")
    val listingId: Long,
    val title: String,
    @ColumnInfo(name = "description", defaultValue = "''")
    val description: String = "",
    val price: Int?,
    val currency: String?,
    @ColumnInfo(name = "city_name")
    val cityName: String,
    val url: String,
    @ColumnInfo(name = "photo_url")
    val photoUrl: String?,
    val reason: String, // "new" or "pushup"
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "found_at")
    val foundAt: Long = System.currentTimeMillis(),

    // CONTRACT addition
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,

    // «Сенсор состояния»: ошибка/рабочий, вычислено локальным анализатором при опросе
    @ColumnInfo(name = "condition_score")
    val conditionScore: Int? = null,

    // AI analysis fields
    @ColumnInfo(name = "ai_score")
    val aiScore: Int? = null,
    @ColumnInfo(name = "ai_status")
    val aiStatus: String? = null,
    @ColumnInfo(name = "ai_product")
    val aiProduct: String? = null,
    @ColumnInfo(name = "ai_model")
    val aiModel: String? = null,
    @ColumnInfo(name = "ai_photo_match")
    val aiPhotoMatch: Boolean? = null,
    @ColumnInfo(name = "ai_reasons")
    val aiReasons: String? = null,
    @ColumnInfo(name = "ai_reject_reason")
    val aiRejectReason: String? = null,
    @ColumnInfo(name = "ai_details_json")
    val aiDetailsJson: String? = null
)

@Entity(
    tableName = "seen_items",
    primaryKeys = ["watch_id", "listing_id"],
    indices = [
        Index(value = ["watch_id", "listing_id"], name = "idx_seen_items_lookup"),
        Index(value = ["watch_id", "added_at"], name = "idx_seen_items_watch_added")
    ]
)
data class SeenItem(
    @ColumnInfo(name = "watch_id")
    val watchId: Long,
    @ColumnInfo(name = "listing_id")
    val listingId: Long,
    @ColumnInfo(name = "last_pushed_at")
    val lastPushedAt: Long? = null,
    @ColumnInfo(name = "last_price")
    val lastPrice: Int? = null,
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)

data class Listing(
    val id: Long,
    val url: String,
    val title: String,
    val description: String,
    val price: Int?,
    val currency: String?,
    val cityName: String,
    val cityId: Int?,
    val categoryId: Int?,
    val isBusiness: Boolean,
    val state: String?,
    val photoUrl: String?,
    val photoUrls: List<String> = emptyList(),
    val createdAt: Long,
    val pushedAt: Long?,
    val refreshedAt: Long?
)

/**
 * Категории поиска OLX.ua (проверено по API: offers?category_id=...).
 * ALL — без ограничения категории.
 */
enum class OlxCategory(val id: Int, val label: String) {
    ALL(0, "Все"),
    COMPUTERS(38, "Компьютеры"),
    PC_COMPONENTS(458, "Комплектующие ПК"),
    PHONES(44, "Телефоны")
}
