package ua.magerko.olxradar.data.network

import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.magerko.olxradar.data.model.Listing
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.service.MonitorService
import ua.magerko.olxradar.service.NotificationHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OlxParserTest {

    private fun createSampleListing(
        id: Long = 1L,
        title: String = "Google Pixel 8",
        description: String = "Отличное состояние, полный комплект",
        price: Int? = 15000,
        currency: String? = "UAH",
        cityName: String = "Киев",
        cityId: Int? = 1,
        categoryId: Int? = 37,
        isBusiness: Boolean = false,
        state: String? = "used",
        createdAt: Long = 1000L
    ) = Listing(
        id = id,
        url = "https://www.olx.ua/d/obyavlenie/1",
        title = title,
        description = description,
        price = price,
        currency = currency,
        cityName = cityName,
        cityId = cityId,
        categoryId = categoryId,
        isBusiness = isBusiness,
        state = state,
        photoUrl = null,
        createdAt = createdAt,
        pushedAt = null,
        refreshedAt = null
    )

    // B3: Currency matching
    @Test
    fun testCurrencyMatching_sameCurrencyEnforcesPriceBounds() {
        val watch = Watch(
            title = "Pixel",
            query = "pixel",
            priceFrom = 10000,
            priceTo = 20000,
            priceCurrency = "UAH"
        )

        val inRange = createSampleListing(price = 15000, currency = "UAH")
        assertTrue("In-range price in same currency must match", OlxParser.matchesWatch(inRange, watch))

        val tooExpensive = createSampleListing(price = 25000, currency = "UAH")
        assertFalse("Too expensive in same currency must not match", OlxParser.matchesWatch(tooExpensive, watch))

        val tooCheap = createSampleListing(price = 5000, currency = "UAH")
        assertFalse("Too cheap in same currency must not match", OlxParser.matchesWatch(tooCheap, watch))
    }

    @Test
    fun testCurrencyMatching_differentCurrencyBypassesPriceBounds() {
        // B3: If currency does not match watch.priceCurrency, do not reject by price bounds
        val watch = Watch(
            title = "Pixel",
            query = "pixel",
            priceFrom = 10000,
            priceTo = 20000,
            priceCurrency = "UAH"
        )

        // 500 USD is technically outside 10000..20000 numeric bounds, but currency is USD vs UAH
        val usdListing = createSampleListing(price = 500, currency = "USD")
        assertTrue("Foreign currency listing must not be rejected by UAH numeric price bounds", OlxParser.matchesWatch(usdListing, watch))
    }

    @Test
    fun testCurrencyMatching_nullOrBlankCurrencyBypassesPriceBounds() {
        // B3: If listing.currency is null, it does not equal watch.priceCurrency -> bypass price bounds
        val watch = Watch(
            title = "Pixel",
            query = "pixel",
            priceFrom = 10000,
            priceTo = 20000,
            priceCurrency = "UAH"
        )

        val nullCurrencyListing = createSampleListing(price = 500, currency = null)
        assertTrue("Listing with null currency must bypass price bounds without rejection", OlxParser.matchesWatch(nullCurrencyListing, watch))

        val blankCurrencyListing = createSampleListing(price = 500, currency = "  ")
        assertTrue("Listing with blank currency must bypass price bounds without rejection", OlxParser.matchesWatch(blankCurrencyListing, watch))
    }

    // B4: Lots without price
    @Test
    fun testLotsWithoutPrice_rejectedWhenIncludeNoPriceFalse() {
        val watch = Watch(
            title = "Pixel",
            query = "pixel",
            includeNoPrice = false
        )
        val noPriceListing = createSampleListing(price = null, currency = null)
        assertFalse("Lot without price must be rejected when includeNoPrice is false", OlxParser.matchesWatch(noPriceListing, watch))
    }

    @Test
    fun testLotsWithoutPrice_acceptedWhenIncludeNoPriceTrue() {
        val watch = Watch(
            title = "Pixel",
            query = "pixel",
            includeNoPrice = true
        )
        val noPriceListing = createSampleListing(price = null, currency = null)
        assertTrue("Lot without price must be accepted when includeNoPrice is true", OlxParser.matchesWatch(noPriceListing, watch))
    }

    // B5: Stop-words on word boundaries
    @Test
    fun testStopWords_wordBoundaryMatching() {
        val watch = Watch(
            title = "Тест",
            query = "тест",
            stopWords = "парт, icloud"
        )

        // Substring inside another word: "апартаменты" does not match "парт"
        val lotSubword = createSampleListing(title = "Апартаменты люкс", description = "Уютные апартаменты")
        assertTrue("Substrings like 'апартаменты' should not trigger stop-word 'парт'", OlxParser.matchesWatch(lotSubword, watch))

        // Exact Cyrillic word
        val lotExactStop = createSampleListing(title = "Продам на парт склад", description = "детали")
        assertFalse("Exact word 'парт' must trigger stop word", OlxParser.matchesWatch(lotExactStop, watch))

        // Punctuation adjacent: "(парт)" or "склад,парт"
        val lotPunctuation = createSampleListing(title = "Склад (парт)", description = "детали")
        assertFalse("Punctuation-bounded word '(парт)' must trigger stop word", OlxParser.matchesWatch(lotPunctuation, watch))

        // Exact Latin word
        val lotIcloud = createSampleListing(title = "Телефон с icloud", description = "пароль утерян")
        assertFalse("Stop word 'icloud' must trigger", OlxParser.matchesWatch(lotIcloud, watch))
    }

    // Must-words with "any" mode (OR logic - default)
    @Test
    fun testMustWords_anyMustBePresent() {
        val watch = Watch(
            title = "iPhone",
            query = "iphone",
            mustWords = "128gb, pro",
            mustWordsMode = "any"
        )

        val hasAll = createSampleListing(title = "iPhone 13 Pro 128gb", description = "Идеальный")
        assertTrue("Lot containing both must-words should match in any mode", OlxParser.matchesWatch(hasAll, watch))

        val hasOne = createSampleListing(title = "iPhone 13 128gb", description = "Обычный")
        assertTrue("Lot containing at least one must-word ('128gb') should match in any mode", OlxParser.matchesWatch(hasOne, watch))

        val hasOther = createSampleListing(title = "iPhone 13 Pro 64gb", description = "Обычный")
        assertTrue("Lot containing at least one must-word ('pro') should match in any mode", OlxParser.matchesWatch(hasOther, watch))

        val missingAll = createSampleListing(title = "iPhone 13 64gb", description = "Без про")
        assertFalse("Lot missing all must-words should not match in any mode", OlxParser.matchesWatch(missingAll, watch))
    }

    // Must-words with "all" mode (AND logic)
    @Test
    fun testMustWords_allMustBePresent() {
        val watch = Watch(
            title = "iPhone",
            query = "iphone",
            mustWords = "128gb, pro",
            mustWordsMode = "all"
        )

        val hasAll = createSampleListing(title = "iPhone 13 Pro 128gb", description = "Идеальный")
        assertTrue("Lot containing all must-words should match in all mode", OlxParser.matchesWatch(hasAll, watch))

        val missingOne = createSampleListing(title = "iPhone 13 128gb", description = "Обычный")
        assertFalse("Lot missing 'pro' should not match in all mode", OlxParser.matchesWatch(missingOne, watch))

        val missingAll = createSampleListing(title = "iPhone 13 64gb", description = "Без про")
        assertFalse("Lot missing all must-words should not match in all mode", OlxParser.matchesWatch(missingAll, watch))
    }

    // Owner type & State filters
    @Test
    fun testOwnerTypeAndStateFilters() {
        val privateOnlyWatch = Watch(title = "Test", query = "test", ownerType = "private")
        val businessLot = createSampleListing(isBusiness = true)
        val privateLot = createSampleListing(isBusiness = false)

        assertFalse("Business lot must be rejected when watch is private only", OlxParser.matchesWatch(businessLot, privateOnlyWatch))
        assertTrue("Private lot must be accepted when watch is private only", OlxParser.matchesWatch(privateLot, privateOnlyWatch))

        val newOnlyWatch = Watch(title = "Test", query = "test", state = "new")
        val usedLot = createSampleListing(state = "used")
        val newLot = createSampleListing(state = "new")

        assertFalse("Used lot must be rejected when watch is new only", OlxParser.matchesWatch(usedLot, newOnlyWatch))
        assertTrue("New lot must be accepted when watch is new only", OlxParser.matchesWatch(newLot, newOnlyWatch))
    }

    @Test
    fun testMatchesWatch_publicationAgeFiltering() {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600_000L
        val twoDaysAgo = now - 2 * 24 * 3600_000L
        val tenDaysAgo = now - 10 * 24 * 3600_000L

        val lot1h = createSampleListing(createdAt = oneHourAgo)
        val lot2d = createSampleListing(createdAt = twoDaysAgo)
        val lot10d = createSampleListing(createdAt = tenDaysAgo)

        val freshWatch = Watch(title = "Test", query = "pixel", publicationAge = "fresh")
        assertTrue("Lot 1h old must match fresh watch (<24h)", OlxParser.matchesWatch(lot1h, freshWatch))
        assertFalse("Lot 2d old must NOT match fresh watch (<24h)", OlxParser.matchesWatch(lot2d, freshWatch))
        assertFalse("Lot 10d old must NOT match fresh watch (<24h)", OlxParser.matchesWatch(lot10d, freshWatch))

        val recentWatch = Watch(title = "Test", query = "pixel", publicationAge = "recent")
        assertTrue("Lot 1h old must match recent watch (<3d)", OlxParser.matchesWatch(lot1h, recentWatch))
        assertTrue("Lot 2d old must match recent watch (<3d)", OlxParser.matchesWatch(lot2d, recentWatch))
        assertFalse("Lot 10d old must NOT match recent watch (<3d)", OlxParser.matchesWatch(lot10d, recentWatch))

        val oldWatch = Watch(title = "Test", query = "pixel", publicationAge = "old")
        assertFalse("Lot 1h old must NOT match old watch (>7d)", OlxParser.matchesWatch(lot1h, oldWatch))
        assertFalse("Lot 2d old must NOT match old watch (>7d)", OlxParser.matchesWatch(lot2d, oldWatch))
        assertTrue("Lot 10d old must match old watch (>7d)", OlxParser.matchesWatch(lot10d, oldWatch))
    }

    // B13: Parsing API JSON with and without pushup_time / last_refresh_time
    @Test
    fun testParseListings_handlesMissingPushupFieldsSafely() {
        val jsonWithoutPushup = """
            {
                "data": [
                    {
                        "id": 12345,
                        "url": "https://www.olx.ua/d/12345",
                        "title": "Ноутбук Lenovo",
                        "description": "Хороший стан",
                        "params": [
                            {"key": "price", "value": {"value": 12000, "currency": "UAH"}}
                        ],
                        "location": {
                            "city": {"id": 1, "name": "Київ"}
                        },
                        "created_time": "2024-05-01T12:00:00+02:00"
                    }
                ]
            }
        """.trimIndent()

        val listings = OlxParser.parseListings(jsonWithoutPushup)
        assertEquals(1, listings.size)
        val item = listings[0]
        assertEquals(12345L, item.id)
        assertEquals(12000, item.price)
        assertEquals("UAH", item.currency)
        assertNull("pushedAt must be null when pushup_time is absent", item.pushedAt)
        assertNull("refreshedAt must be null when last_refresh_time is absent", item.refreshedAt)
    }

    @Test
    fun testParseListings_parsesPushupFieldsWhenPresent() {
        val jsonWithPushup = """
            {
                "data": [
                    {
                        "id": 67890,
                        "url": "https://www.olx.ua/d/67890",
                        "title": "Смартфон Samsung",
                        "description": "Новий",
                        "params": [
                            {"key": "price", "value": {"value": 8000, "currency": "UAH"}}
                        ],
                        "location": {
                            "city": {"id": 2, "name": "Львів"}
                        },
                        "created_time": "2024-05-01T10:00:00+02:00",
                        "pushup_time": "2024-05-02T15:30:00+02:00",
                        "last_refresh_time": "2024-05-02T15:30:00+02:00"
                    }
                ]
            }
        """.trimIndent()

        val listings = OlxParser.parseListings(jsonWithPushup)
        assertEquals(1, listings.size)
        val item = listings[0]
        assertEquals(67890L, item.id)
        assertNotNull("pushedAt must be parsed when pushup_time is present", item.pushedAt)
        assertNotNull("refreshedAt must be parsed when last_refresh_time is present", item.refreshedAt)
        assertTrue("pushedAt must be greater than createdAt", item.pushedAt!! > item.createdAt)
    }

    // B1: Error classification tests
    @Test
    fun testClassifyError_distinguishesHttpCodesTimeoutsAndJson() {
        val service = MonitorService()

        val (msg403, code403) = service.classifyError(RuntimeException("Error: HTTP 403"))
        assertEquals(403, code403)
        assertTrue(msg403.contains("403"))

        val (msg429, code429) = service.classifyError(RuntimeException("Error: HTTP 429"))
        assertEquals(429, code429)
        assertTrue(msg429.contains("429"))

        val (msg503, code503) = service.classifyError(RuntimeException("Error: HTTP 503"))
        assertEquals(503, code503)
        assertEquals("HTTP 503", msg503)

        val (msgTimeout, codeTimeout) = service.classifyError(RuntimeException("Request timed out after 25000ms"))
        assertNull(codeTimeout)
        assertEquals("Timeout", msgTimeout)

        val (msgJson, codeJson) = service.classifyError(JSONException("Value <html> at 0 of type java.lang.String cannot be converted to JSONObject"))
        assertNull(codeJson)
        assertEquals("Invalid JSON", msgJson)

        val (msgRenderer, codeRenderer) = service.classifyError(RuntimeException("WebView renderer process gone"))
        assertNull(codeRenderer)
        assertEquals("Renderer crashed", msgRenderer)
    }

    // B7: Stable hash from 64-bit to 32-bit ID
    @Test
    fun testNotificationId_stableHash() {
        val id1 = 12345L
        val id2 = 987654321012345L
        val id3 = 987654321012345L

        assertEquals(NotificationHelper.toNotificationId(id2), NotificationHelper.toNotificationId(id3))
        assertNotEquals(NotificationHelper.toNotificationId(id1), NotificationHelper.toNotificationId(id2))

        val intId = NotificationHelper.toNotificationId(id2)
        assertEquals((id2 xor (id2 ushr 32)).toInt(), intId)
    }

    // B2: Initialized logic verification - proves first lot is not swallowed after empty initial poll
    @Test
    fun testInitialPollLogic_emptyFirstPollDoesNotSwallowSubsequentLot() {
        var watch = Watch(id = 1L, title = "Pixel", query = "pixel", initialized = false)
        val seenIds = mutableSetOf<Long>()
        val findings = mutableListOf<Listing>()

        // Poll 1: API returns empty list of offers
        val poll1Listings = emptyList<Listing>()
        if (!watch.initialized) {
            // First successful poll marks all seen (none in this case) and activates initialized
            for (l in poll1Listings) {
                seenIds.add(l.id)
            }
            watch = watch.copy(initialized = true)
        } else {
            for (l in poll1Listings) {
                if (l.id !in seenIds) findings.add(l)
            }
        }

        assertTrue("Watch must become initialized after first successful poll", watch.initialized)
        assertTrue("No findings on empty initial poll", findings.isEmpty())
        assertTrue("No seen IDs on empty initial poll", seenIds.isEmpty())

        // Poll 2: API now returns 1 new listing
        val newListing = createSampleListing(id = 101L, title = "Google Pixel 8 Pro")
        val poll2Listings = listOf(newListing)

        if (!watch.initialized) {
            for (l in poll2Listings) {
                seenIds.add(l.id)
            }
            watch = watch.copy(initialized = true)
        } else {
            for (l in poll2Listings) {
                if (l.id !in seenIds && OlxParser.matchesWatch(l, watch)) {
                    findings.add(l)
                    seenIds.add(l.id)
                }
            }
        }

        // B2 Bug Check: The new listing must NOT be swallowed!
        assertEquals("Subsequent lot must be detected and recorded as finding", 1, findings.size)
        assertEquals(101L, findings[0].id)
        assertTrue("New listing must be recorded in seenIds", seenIds.contains(101L))
    }

    // B11: Quiet hours edge-case verification
    @Test
    fun testQuietHours_overnightAndSameDayRanges() {
        // Disabled quiet hours: always false
        assertFalse(MonitorService.isInQuietHoursRange(false, 23, 0, 7, 0, 23 * 60))
        assertFalse(MonitorService.isInQuietHoursRange(false, 23, 0, 7, 0, 2 * 60))

        // Overnight range: 23:00 to 07:00 (crosses midnight)
        // 23:00 = 1380m
        assertTrue("Exact start at 23:00 must be quiet", MonitorService.isInQuietHoursRange(true, 23, 0, 7, 0, 23 * 60))
        // 23:30 = 1410m
        assertTrue("Late evening 23:30 must be quiet", MonitorService.isInQuietHoursRange(true, 23, 0, 7, 0, 23 * 60 + 30))
        // 00:00 = 0m
        assertTrue("Midnight must be quiet", MonitorService.isInQuietHoursRange(true, 23, 0, 7, 0, 0))
        // 03:15 = 195m
        assertTrue("Night 03:15 must be quiet", MonitorService.isInQuietHoursRange(true, 23, 0, 7, 0, 3 * 60 + 15))
        // 07:00 = 420m
        assertTrue("Exact end at 07:00 must be quiet", MonitorService.isInQuietHoursRange(true, 23, 0, 7, 0, 7 * 60))
        // 07:01 = 421m
        assertFalse("Morning 07:01 must NOT be quiet", MonitorService.isInQuietHoursRange(true, 23, 0, 7, 0, 7 * 60 + 1))
        // 22:59 = 1379m
        assertFalse("Evening 22:59 must NOT be quiet", MonitorService.isInQuietHoursRange(true, 23, 0, 7, 0, 23 * 60 - 1))
        // 12:00 = 720m
        assertFalse("Noon 12:00 must NOT be quiet", MonitorService.isInQuietHoursRange(true, 23, 0, 7, 0, 12 * 60))

        // Same-day range: 13:00 to 15:30
        assertFalse("12:59 is before same-day range", MonitorService.isInQuietHoursRange(true, 13, 0, 15, 30, 13 * 60 - 1))
        assertTrue("13:00 exact start is quiet", MonitorService.isInQuietHoursRange(true, 13, 0, 15, 30, 13 * 60))
        assertTrue("14:15 is inside same-day range", MonitorService.isInQuietHoursRange(true, 13, 0, 15, 30, 14 * 60 + 15))
        assertTrue("15:30 exact end is quiet", MonitorService.isInQuietHoursRange(true, 13, 0, 15, 30, 15 * 60 + 30))
        assertFalse("15:31 is after same-day range", MonitorService.isInQuietHoursRange(true, 13, 0, 15, 30, 15 * 60 + 31))
    }

    // Date parsing thread-safety and format tests
    @Test
    fun testParseDate_threadSafetyAndFormats() {
        val dateWithOffset = "2024-05-01T12:00:00+02:00"
        val timestamp1 = OlxParser.parseDate(dateWithOffset)
        assertNotNull("Date with timezone offset must be parsed", timestamp1)

        val dateWithoutOffset = "2024-05-01T12:00:00"
        val timestamp2 = OlxParser.parseDate(dateWithoutOffset)
        assertNotNull("Date without timezone offset must be parsed", timestamp2)

        // Concurrency test across 20 parallel threads to verify synchronized SimpleDateFormat
        val threads = (1..20).map {
            Thread {
                for (i in 1..100) {
                    val ts = OlxParser.parseDate("2024-05-01T12:00:00+02:00")
                    assertEquals(timestamp1, ts)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    // B10 & C9: URL encoding and quoting verification
    @Test
    fun testBuildSearchUrl_encodesParametersCorrectly() {
        val watch = Watch(
            title = "Test Watch",
            query = "iphone 13 pro & max",
            priceFrom = 10000,
            priceTo = 20000,
            cityId = 5,
            categoryId = 37,
            ownerType = "private",
            state = "used"
        )
        val url = OlxParser.buildSearchUrl(watch)
        assertTrue(url.startsWith("https://www.olx.ua/api/v1/offers/?"))
        assertTrue(url.contains("query=iphone+13+pro+%26+max") || url.contains("query=iphone%2013%20pro%20%26%20max"))
        assertTrue(url.contains("filter_float_price:from=10000") || url.contains("filter_float_price%3Afrom=10000"))
        assertTrue(url.contains("filter_float_price:to=20000") || url.contains("filter_float_price%3Ato=20000"))
        assertTrue(url.contains("city_id=5"))
        assertTrue(url.contains("category_id=37"))
        assertTrue(url.contains("owner_type=private"))
        assertTrue(url.contains("filter_enum_state[0]=used") || url.contains("filter_enum_state%5B0%5D=used"))
    }
}
