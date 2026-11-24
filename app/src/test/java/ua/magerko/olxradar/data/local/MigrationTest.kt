package ua.magerko.olxradar.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.SeenItem
import ua.magerko.olxradar.data.model.Watch

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private class V1SqliteHelper(context: Context, dbName: String) :
        SQLiteOpenHelper(context, dbName, null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE watches (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    query TEXT NOT NULL,
                    price_from INTEGER,
                    price_to INTEGER,
                    city_id INTEGER,
                    city_name TEXT,
                    category_id INTEGER,
                    owner_type TEXT,
                    state TEXT,
                    stop_words TEXT,
                    notify_mode TEXT NOT NULL,
                    is_active INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """)

            db.execSQL("""
                CREATE TABLE findings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    watch_id INTEGER NOT NULL,
                    listing_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    price INTEGER,
                    currency TEXT,
                    city_name TEXT,
                    url TEXT NOT NULL,
                    photo_url TEXT,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    found_at INTEGER NOT NULL
                )
            """)

            db.execSQL("""
                CREATE TABLE seen_items (
                    watch_id INTEGER NOT NULL,
                    listing_id INTEGER NOT NULL,
                    last_pushed_at INTEGER,
                    PRIMARY KEY (watch_id, listing_id)
                )
            """)

            db.execSQL("CREATE INDEX idx_findings_found_at ON findings(found_at DESC)")
            db.execSQL("CREATE INDEX idx_seen_items_lookup ON seen_items(watch_id, listing_id)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    @Test
    fun testMigration1To2_preservesData_andInitializesWatchesWithSeenItems() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_db"
        context.deleteDatabase(dbName)

        // 1. Create database in v1 with legacy SQLiteOpenHelper
        val helper = V1SqliteHelper(context, dbName)
        val v1Db = helper.writableDatabase

        // Insert Watch 1 (will have seen items)
        val watch1Cv = ContentValues().apply {
            put("title", "iPhone 13")
            put("query", "iphone 13")
            put("price_from", 10000)
            put("price_to", 20000)
            put("city_id", 1)
            put("city_name", "Киев")
            put("category_id", 37)
            put("owner_type", "private")
            put("state", "used")
            put("stop_words", "icloud, parts")
            put("notify_mode", "new_pushup")
            put("is_active", 1)
            put("created_at", 1000L)
        }
        val watch1Id = v1Db.insert("watches", null, watch1Cv)

        // Insert Watch 2 (will NOT have seen items)
        val watch2Cv = ContentValues().apply {
            put("title", "MacBook Pro")
            put("query", "macbook pro")
            put("stop_words", "")
            put("notify_mode", "new")
            put("is_active", 1)
            put("created_at", 2000L)
        }
        val watch2Id = v1Db.insert("watches", null, watch2Cv)

        // Insert SeenItem for Watch 1
        val seenCv = ContentValues().apply {
            put("watch_id", watch1Id)
            put("listing_id", 9991L)
            put("last_pushed_at", 1500L)
        }
        v1Db.insert("seen_items", null, seenCv)

        // Insert Finding for Watch 1
        val findingCv = ContentValues().apply {
            put("watch_id", watch1Id)
            put("listing_id", 9991L)
            put("title", "iPhone 13 128GB")
            put("price", 15000)
            put("currency", "UAH")
            put("city_name", "Киев")
            put("url", "https://olx.ua/d/123")
            put("photo_url", "https://img.olx.ua/1.jpg")
            put("reason", "new")
            put("created_at", 1200L)
            put("found_at", 1300L)
        }
        v1Db.insert("findings", null, findingCv)

        v1Db.close()
        helper.close()

        // 2. Open with Room v11 adding all migrations
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .build()

        runBlocking {
            // Verify watches
            val watches = roomDb.watchDao().getAllWatches()
            assertEquals(2, watches.size)

            val watch1 = watches.find { it.id == watch1Id }
            assertNotNull(watch1)
            assertEquals("iphone 13", watch1?.query)
            assertEquals(10000, watch1?.priceFrom)
            assertEquals(20000, watch1?.priceTo)
            assertEquals("Киев", watch1?.cityName)
            // A7 check: Watch 1 had seen_items -> initialized must be TRUE
            assertTrue("Watch 1 with seen items must be initialized", watch1!!.initialized)
            assertEquals("UAH", watch1.priceCurrency)
            assertFalse(watch1.includeNoPrice)
            assertEquals("", watch1.mustWords)
            assertEquals("any", watch1.mustWordsMode)

            val watch2 = watches.find { it.id == watch2Id }
            assertNotNull(watch2)
            assertEquals("macbook pro", watch2?.query)
            // Watch 2 had no seen_items -> initialized must be FALSE
            assertFalse("Watch 2 with no seen items must not be initialized", watch2!!.initialized)

            // Verify findings
            val findings = roomDb.findingDao().getAllFindings()
            assertEquals(1, findings.size)
            val finding = findings[0]
            assertEquals(9991L, finding.listingId)
            assertEquals("iPhone 13 128GB", finding.title)
            assertEquals(15000, finding.price)
            assertEquals("", finding.description)
            assertFalse("Default is_read must be false", finding.isRead)
            assertNull("Default ai_score must be null", finding.aiScore)
            assertNull("Default ai_status must be null", finding.aiStatus)
            assertFalse("Default is_delivery must be false", finding.isDelivery)
            assertFalse("Default is_favorite must be false", finding.isFavorite)
            assertNull("Default old_price must be null", finding.oldPrice)
            assertNull("Default deal_score must be null", finding.dealScore)
            assertNull("Default photo_urls_json must be null", finding.photoUrlsJson)
            assertNull("Default seller_name must be null", finding.sellerName)

            roomDb.sellerBlacklistDao().upsert(ua.magerko.olxradar.data.model.SellerBlacklist("scammer", "Scammer"))
            assertTrue(roomDb.sellerBlacklistDao().isBlocked("scammer"))
            roomDb.priceHistoryDao().insert(ua.magerko.olxradar.data.model.PriceHistory(watchId = watch1Id, listingId = 9991L, price = 15000, currency = "UAH"))
            assertNotNull(roomDb.priceHistoryDao().getLatestForListing(9991L))

            // Verify AI Cache table exists and works
            roomDb.aiCacheDao().insert(AiCacheEntry(9991L, "{\"is_match\":true,\"confidence\":95}"))
            val cachedEntry = roomDb.aiCacheDao().getByListingId(9991L)
            assertNotNull(cachedEntry)
            assertEquals("{\"is_match\":true,\"confidence\":95}", cachedEntry?.resultJson)

            // Verify seen items
            val seenIds = roomDb.seenItemDao().getSeenIds(watch1Id)
            assertTrue(seenIds.contains(9991L))
            val lastPushed = roomDb.seenItemDao().getLastPushedAt(watch1Id, 9991L)
            assertEquals(1500L, lastPushed)

            // A3: Test cascade delete of watch
            roomDb.watchDao().deleteWatchCascade(watch1Id)

            val watchesAfterDelete = roomDb.watchDao().getAllWatches()
            assertEquals(1, watchesAfterDelete.size)
            assertEquals(watch2Id, watchesAfterDelete[0].id)

            val findingsAfterDelete = roomDb.findingDao().getAllFindings()
            assertTrue("Findings for deleted watch must be cascade deleted", findingsAfterDelete.isEmpty())

            val seenAfterDelete = roomDb.seenItemDao().getSeenIds(watch1Id)
            assertTrue("Seen items for deleted watch must be cascade deleted", seenAfterDelete.isEmpty())
        }

        roomDb.close()
    }

    @Test
    fun testPruneOldSeenItems_keepsAtMostLimit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "prune_test_db"
        context.deleteDatabase(dbName)

        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()

        runBlocking {
            val watchId = roomDb.watchDao().insertWatch(
                Watch(title = "Test", query = "test")
            )

            // Insert 15 items with incrementing addedAt
            for (i in 1..15) {
                roomDb.seenItemDao().insertOrReplace(
                    SeenItem(
                        watchId = watchId,
                        listingId = i.toLong(),
                        lastPushedAt = null,
                        lastPrice = 100 * i,
                        addedAt = i * 1000L
                    )
                )
            }

            // Prune to limit = 10
            roomDb.seenItemDao().pruneOldSeenItems(watchId, limit = 10)

            val remaining = roomDb.seenItemDao().getSeenIds(watchId)
            assertEquals(10, remaining.size)

            // Should keep the latest 10 (listings 6 to 15), older (1 to 5) pruned
            for (i in 6..15) {
                assertTrue("Should keep listing $i", remaining.contains(i.toLong()))
            }
            for (i in 1..5) {
                assertFalse("Should have pruned listing $i", remaining.contains(i.toLong()))
            }
        }

        roomDb.close()
    }
}
