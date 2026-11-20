package ua.magerko.olxradar.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // A1, A2: Migration without data loss. Recreates tables with full schema compatibility
        // preserving all existing data (unlike legacy onUpgrade which dropped tables without saving data).

        // 1. Watches table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `watches_temp` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `query` TEXT NOT NULL,
                `price_from` INTEGER,
                `price_to` INTEGER,
                `city_id` INTEGER,
                `city_name` TEXT,
                `category_id` INTEGER,
                `owner_type` TEXT,
                `state` TEXT,
                `stop_words` TEXT NOT NULL,
                `notify_mode` TEXT NOT NULL,
                `is_active` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `initialized` INTEGER NOT NULL,
                `last_success_at` INTEGER,
                `last_error` TEXT,
                `last_http_code` INTEGER,
                `include_no_price` INTEGER NOT NULL,
                `price_currency` TEXT NOT NULL DEFAULT 'UAH',
                `must_words` TEXT NOT NULL,
                `snoozed_until` INTEGER,
                `interval_sec_override` INTEGER
            )
        """.trimIndent())

        // A7: Existing watches that already have seen_items receive initialized = 1, otherwise 0
        db.execSQL("""
            INSERT INTO `watches_temp` (
                `id`, `title`, `query`, `price_from`, `price_to`, `city_id`, `city_name`,
                `category_id`, `owner_type`, `state`, `stop_words`, `notify_mode`, `is_active`,
                `created_at`, `initialized`, `last_success_at`, `last_error`, `last_http_code`,
                `include_no_price`, `price_currency`, `must_words`, `snoozed_until`, `interval_sec_override`
            )
            SELECT 
                `id`, `title`, `query`, `price_from`, `price_to`, `city_id`, `city_name`,
                `category_id`, `owner_type`, `state`, COALESCE(`stop_words`, ''), `notify_mode`, `is_active`,
                `created_at`,
                CASE WHEN `id` IN (SELECT DISTINCT `watch_id` FROM `seen_items`) THEN 1 ELSE 0 END,
                NULL, NULL, NULL, 0, 'UAH', '', NULL, NULL
            FROM `watches`
        """.trimIndent())

        db.execSQL("DROP TABLE `watches`")
        db.execSQL("ALTER TABLE `watches_temp` RENAME TO `watches`")

        // 2. Findings table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `findings_temp` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `watch_id` INTEGER NOT NULL,
                `listing_id` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `price` INTEGER,
                `currency` TEXT,
                `city_name` TEXT NOT NULL,
                `url` TEXT NOT NULL,
                `photo_url` TEXT,
                `reason` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `found_at` INTEGER NOT NULL,
                `is_read` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `findings_temp` (
                `id`, `watch_id`, `listing_id`, `title`, `price`, `currency`, `city_name`,
                `url`, `photo_url`, `reason`, `created_at`, `found_at`, `is_read`
            )
            SELECT 
                `id`, `watch_id`, `listing_id`, `title`, `price`, `currency`, `city_name`,
                `url`, `photo_url`, `reason`, `created_at`, `found_at`, 0
            FROM `findings`
        """.trimIndent())

        db.execSQL("DROP TABLE `findings`")
        db.execSQL("ALTER TABLE `findings_temp` RENAME TO `findings`")

        // Indices on findings (A5)
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_findings_found_at` ON `findings` (`found_at` DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_findings_watch_listing` ON `findings` (`watch_id`, `listing_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_findings_watch_id` ON `findings` (`watch_id`)")

        // 3. Seen items table (A6: last_price, added_at)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `seen_items_temp` (
                `watch_id` INTEGER NOT NULL,
                `listing_id` INTEGER NOT NULL,
                `last_pushed_at` INTEGER,
                `last_price` INTEGER,
                `added_at` INTEGER NOT NULL,
                PRIMARY KEY(`watch_id`, `listing_id`)
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `seen_items_temp` (
                `watch_id`, `listing_id`, `last_pushed_at`, `last_price`, `added_at`
            )
            SELECT 
                `watch_id`, `listing_id`, `last_pushed_at`, NULL, 0
            FROM `seen_items`
        """.trimIndent())

        db.execSQL("DROP TABLE `seen_items`")
        db.execSQL("ALTER TABLE `seen_items_temp` RENAME TO `seen_items`")

        // Indices on seen_items (A5)
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_seen_items_lookup` ON `seen_items` (`watch_id`, `listing_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_seen_items_watch_added` ON `seen_items` (`watch_id`, `added_at`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `findings` ADD COLUMN `ai_score` INTEGER")
        db.execSQL("ALTER TABLE `findings` ADD COLUMN `ai_status` TEXT")
        db.execSQL("ALTER TABLE `findings` ADD COLUMN `ai_product` TEXT")
        db.execSQL("ALTER TABLE `findings` ADD COLUMN `ai_model` TEXT")
        db.execSQL("ALTER TABLE `findings` ADD COLUMN `ai_photo_match` INTEGER")
        db.execSQL("ALTER TABLE `findings` ADD COLUMN `ai_reasons` TEXT")
        db.execSQL("ALTER TABLE `findings` ADD COLUMN `ai_reject_reason` TEXT")
        db.execSQL("ALTER TABLE `findings` ADD COLUMN `ai_details_json` TEXT")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ai_cache` (
                `listing_id` INTEGER NOT NULL,
                `result_json` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`listing_id`)
            )
        """.trimIndent())
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `findings` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Категории и «Сенсор состояния»
        db.execSQL("ALTER TABLE `watches` ADD COLUMN `category_preset` TEXT NOT NULL DEFAULT 'any'")
        db.execSQL("ALTER TABLE `watches` ADD COLUMN `condition_target` TEXT NOT NULL DEFAULT 'any'")
        db.execSQL("ALTER TABLE `watches` ADD COLUMN `condition_threshold` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `findings` ADD COLUMN `condition_score` INTEGER")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Переключатель режима обязательных слов: any (ИЛИ) / all (И)
        db.execSQL("ALTER TABLE `watches` ADD COLUMN `must_words_mode` TEXT NOT NULL DEFAULT 'any'")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Фильтр по дате публикации (свежести): any / fresh / recent / week / old
        db.execSQL("ALTER TABLE `watches` ADD COLUMN `publication_age` TEXT NOT NULL DEFAULT 'any'")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Флаг OLX доставки / безопасной сделки
        db.execSQL("ALTER TABLE `findings` ADD COLUMN `is_delivery` INTEGER NOT NULL DEFAULT 0")
    }
}
