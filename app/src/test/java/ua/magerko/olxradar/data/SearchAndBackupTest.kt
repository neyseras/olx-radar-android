package ua.magerko.olxradar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.magerko.olxradar.data.backup.RadarBackup
import ua.magerko.olxradar.data.local.AppDatabase
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.Listing
import ua.magerko.olxradar.data.model.OlxCategory
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.data.network.OlxCities
import ua.magerko.olxradar.data.network.OlxParser
import ua.magerko.olxradar.data.network.OlxUrlImporter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchAndBackupTest {

    private fun listing(
        id: Long = 10L,
        title: String = "iPhone 13 Pro 128GB",
        description: String = "Ідеальний стан, все працює, неверлок",
        price: Int? = 18000,
        currency: String? = "UAH",
        cityName: String = "Київ",
        isDelivery: Boolean = true,
        isBusiness: Boolean = false
    ) = Listing(
        id = id,
        url = "https://www.olx.ua/d/$id",
        title = title,
        description = description,
        price = price,
        currency = currency,
        cityName = cityName,
        cityId = 268,
        categoryId = OlxCategory.PHONES.id,
        isBusiness = isBusiness,
        state = "used",
        photoUrl = "https://img.olx.ua/1.jpg",
        photoUrls = listOf("https://img.olx.ua/1.jpg", "https://img.olx.ua/2.jpg"),
        createdAt = System.currentTimeMillis(),
        pushedAt = null,
        refreshedAt = null,
        isDelivery = isDelivery
    )

    @Test
    fun testInlineQueryExclusions_andBuildSearchUrl() {
        val watch = Watch(title = "iPhone", query = "iphone 13 -max -чехол")
        val parsed = OlxParser.parseQuery(watch.query)
        assertEquals("iphone 13", parsed.positiveQuery)
        assertEquals(listOf("max", "чехол"), parsed.inlineExclusions)

        val url = OlxParser.buildSearchUrl(watch)
        assertTrue(url.contains("query=iphone+13") || url.contains("query=iphone%2013"))
        assertFalse(url.contains("-max"))

        val valid = listing(title = "iPhone 13 128GB")
        val excludedMax = listing(title = "iPhone 13 Pro Max 128GB")
        assertTrue(OlxParser.matchesWatch(valid, watch))
        assertFalse(OlxParser.matchesWatch(excludedMax, watch))
    }

    @Test
    fun testCityMatching_uaAndRuAliases() {
        assertTrue(OlxCities.citiesMatch("Київ", "Киев"))
        assertTrue(OlxCities.citiesMatch("Харків", "Харьков"))
        assertTrue(OlxCities.citiesMatch("Дніпро", "Днепр"))
        assertTrue(OlxCities.citiesMatch("Одеса", "Одесса"))
        assertFalse(OlxCities.citiesMatch("Львів", "Киев"))

        val kyivWatch = Watch(title = "iPhone", query = "iphone", cityName = "Киев", cityId = null)
        assertTrue(OlxParser.matchesWatch(listing(cityName = "Київ"), kyivWatch))
        assertFalse(OlxParser.matchesWatch(listing(cityName = "Одеса"), kyivWatch))
    }

    @Test
    fun testSmartMatch_antiSpamAndCurrencyConversion() {
        val watch = Watch(
            title = "iPhone 13",
            query = "iphone 13",
            priceFrom = 10000,
            priceTo = 22000,
            priceCurrency = "UAH"
        )
        val options = OlxParser.SearchMatchOptions(
            smartAntiSpam = true,
            convertForeignCurrency = true,
            usdToUahRate = 40f,
            eurToUahRate = 44f,
            strictQueryWordMatch = true
        )

        val spamLot = listing(title = "Скупка iPhone 13 дорого", description = "Выкуп любых телефонов")
        assertFalse("Buyback spam must be filtered out", OlxParser.matchesWatchSmart(spamLot, watch, options))

        val overpricedUsd = listing(price = 700, currency = "USD") // 700 * 40 = 28000 UAH > 22000
        assertFalse("Overpriced USD lot must be rejected after currency conversion", OlxParser.matchesWatchSmart(overpricedUsd, watch, options))

        val fairUsd = listing(price = 450, currency = "USD") // 450 * 40 = 18000 UAH in range
        assertTrue("In-range USD lot must pass currency conversion", OlxParser.matchesWatchSmart(fairUsd, watch, options))
    }

    @Test
    fun testDealScore_rewardsDiscountAndDelivery() {
        val watch = Watch(title = "iPhone 13", query = "iphone 13", priceCurrency = "UAH")
        val bargain = listing(title = "iPhone 13 Pro 128GB", price = 14000, isDelivery = true, isBusiness = false)
        val overpriced = listing(title = "Телефон", price = 25000, isDelivery = false, isBusiness = true)

        val bargainScore = OlxParser.computeDealScore(bargain, watch, marketMedianPrice = 20000, conditionScore = 85)
        val highScore = OlxParser.computeDealScore(overpriced, watch, marketMedianPrice = 20000, conditionScore = 50)

        assertTrue("Bargain deal score ($bargainScore) must be >= 80", bargainScore >= 80)
        assertTrue("Bargain deal score must beat overpriced lot ($highScore)", bargainScore > highScore)
    }

    @Test
    fun testUrlImporter_parsesOlxSearchLink() {
        val url = "https://www.olx.ua/uk/elektronika/telefony-i-aksesuary/q-iphone-14-pro/?search%5Bfilter_float_price:from%5D=15000&search%5Bfilter_float_price:to%5D=30000&search%5Bfilter_enum_state%5D%5B0%5D=used"
        val watch = OlxUrlImporter.parseOlxUrl(url)
        assertNotNull(watch)
        assertEquals("iphone 14 pro", watch?.query)
        assertEquals(15000, watch?.priceFrom)
        assertEquals(30000, watch?.priceTo)
        assertEquals("used", watch?.state)
        assertEquals(OlxCategory.PHONES.id, watch?.categoryId)
    }

    @Test
    fun testFullBackupV2_roundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val w = Watch(id = 7L, title = "RTX 4070", query = "rtx 4070", priceTo = 25000, cityName = "Киев")
        val f = Finding(
            id = 1L,
            watchId = 7L,
            listingId = 999888L,
            title = "ASUS RTX 4070",
            price = 21000,
            currency = "UAH",
            cityName = "Київ",
            url = "https://olx.ua/d/999888",
            photoUrl = "https://img.olx.ua/1.jpg",
            reason = "price_drop",
            createdAt = 1000L,
            foundAt = 2000L,
            isFavorite = true,
            oldPrice = 23500,
            dealScore = 88
        )

        val json = RadarBackup.toFullBackupJson(context, listOf(w), listOf(f), includeFindings = true)
        val parsed = RadarBackup.parseFullBackup(json)

        assertEquals(2, parsed.format)
        assertEquals(1, parsed.watches.size)
        assertEquals("RTX 4070", parsed.watches[0].title)
        assertEquals(1, parsed.findings.size)
        assertTrue(parsed.findings[0].isFavorite)
        assertEquals(23500, parsed.findings[0].oldPrice)
        assertEquals(88, parsed.findings[0].dealScore)
        assertNotNull(parsed.settingsJson)
    }

    @Test
    fun testCitiesMatchExact_andAntiSpamNegationGuard() {
        assertTrue(OlxCities.citiesMatchExact("Київ", "Киев"))
        assertTrue(OlxCities.citiesMatchExact("Миколаїв", "Николаев"))
        assertFalse(OlxCities.citiesMatchExact("Белая Церковь", "Киев"))

        val watch = Watch(title = "iPhone 13", query = "iphone 13", priceCurrency = "UAH")
        val options = OlxParser.SearchMatchOptions(smartAntiSpam = true, strictQueryWordMatch = true)
        val originalNotCopy = listing(
            title = "iPhone 13 128GB",
            description = "Оригинал, не копия, батарея 91%"
        )
        assertTrue(
            "Negated anti-spam word 'не копия' must not reject legitimate listing",
            OlxParser.matchesWatchSmart(originalNotCopy, watch, options)
        )
    }

    @Test
    fun testSeenItemLastCurrency_andFavoritesFirstBackup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val watchId = db.watchDao().insertWatch(Watch(title = "Laptop", query = "laptop"))
            val seenDao = db.seenItemDao()
            seenDao.markSeenAndPrune(
                watchId = watchId,
                listingId = 5001L,
                lastPushedAt = 1000L,
                lastPrice = 550,
                lastCurrency = "USD"
            )
            // Re-mark without price/currency -> should preserve previous price & currency
            seenDao.markSeenAndPrune(
                watchId = watchId,
                listingId = 5001L,
                lastPushedAt = 2000L,
                lastPrice = null,
                lastCurrency = null
            )
            val savedSeen = seenDao.getSeenItem(watchId, 5001L)
            assertNotNull(savedSeen)
            assertEquals(550, savedSeen?.lastPrice)
            assertEquals("USD", savedSeen?.lastCurrency)

            val findingDao = db.findingDao()
            findingDao.insertFinding(
                Finding(
                    watchId = watchId,
                    listingId = 1L,
                    title = "Old Favorite",
                    price = 10000,
                    currency = "UAH",
                    cityName = "Київ",
                    url = "https://olx.ua/d/1",
                    photoUrl = null,
                    reason = "new",
                    createdAt = 100L,
                    foundAt = 100L,
                    isFavorite = true
                )
            )
            findingDao.insertFinding(
                Finding(
                    watchId = watchId,
                    listingId = 2L,
                    title = "Newer Non-Favorite",
                    price = 11000,
                    currency = "UAH",
                    cityName = "Київ",
                    url = "https://olx.ua/d/2",
                    photoUrl = null,
                    reason = "new",
                    createdAt = 5000L,
                    foundAt = 5000L,
                    isFavorite = false
                )
            )
            val backupList = findingDao.getFindingsForBackup(limit = 1)
            assertEquals(1, backupList.size)
            assertTrue(backupList[0].isFavorite)
            assertEquals("Old Favorite", backupList[0].title)
        } finally {
            db.close()
        }
    }

    @Test
    fun testMultiCityMatching_andEncryptedBackupRoundTrip() {
        assertTrue(OlxCities.isMultiCity("Киев, Бровары, Ирпень"))
        assertTrue(OlxCities.citiesMatch("Бровари", "Киев, Бровары, Ирпень"))
        assertTrue(OlxCities.citiesMatch("Київ, Дарницький район", "Киев, Бровары"))
        assertFalse(OlxCities.citiesMatch("Львів", "Киев, Бровары"))
        assertFalse(OlxCities.citiesMatchExact("Київ", "Киев, Бровары"))

        val plain = "{\"app\":\"OLX Radar\",\"format\":2,\"radars\":[],\"secret\":\"gsk_test123\"}"
        assertFalse(RadarBackup.isEncryptedBackup(plain))
        val encrypted = RadarBackup.encryptBackupJson(plain, "MyStrongPass42")
        assertTrue(RadarBackup.isEncryptedBackup(encrypted))
        assertFalse(encrypted.contains("gsk_test123"))

        val decrypted = RadarBackup.decryptBackupJson(encrypted, "MyStrongPass42")
        assertEquals(plain, decrypted)
    }
}
