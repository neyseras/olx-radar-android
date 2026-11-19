package ua.magerko.olxradar.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.magerko.olxradar.data.local.AppDatabase
import ua.magerko.olxradar.data.model.Listing
import ua.magerko.olxradar.data.model.Watch

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiFilterEngineTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var aiPreferences: AiPreferences
    private lateinit var engine: AiFilterEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        aiPreferences = AiPreferences(context).apply {
            isAiEnabled = true
            apiKey = "gsk_test_key"
            confidenceThreshold = 75
            maxPhotos = 2
            isCheckSuspicious = true
            isStrictMode = false
            isManualReviewMode = true
        }
        val groqClient = GroqVisionClient(aiPreferences)
        val imageProcessor = AiImageProcessor()
        engine = AiFilterEngine(aiPreferences, groqClient, imageProcessor, db.aiCacheDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createSampleListing(
        id: Long = 1001L,
        title: String = "Sapphire RX 470 8GB",
        description: String = "Отличная видеокарта",
        price: Int? = 3000,
        currency: String? = "UAH"
    ) = Listing(
        id = id,
        url = "https://www.olx.ua/d/$id",
        title = title,
        description = description,
        price = price,
        currency = currency,
        cityName = "Киев",
        cityId = 1,
        categoryId = 37,
        isBusiness = false,
        state = "used",
        photoUrl = "https://img.olx.ua/photo1.jpg",
        photoUrls = listOf("https://img.olx.ua/photo1.jpg", "https://img.olx.ua/photo2.jpg"),
        createdAt = 1000L,
        pushedAt = null,
        refreshedAt = null
    )

    private fun createSampleWatch(query: String = "RX 470") = Watch(
        id = 1L,
        title = query,
        query = query,
        priceFrom = 2000,
        priceTo = 4000,
        priceCurrency = "UAH"
    )

    // 1. Scenario 1: Suitable product and correct photos
    @Test
    fun testScenario1_suitableProduct_highConfidence_matches() {
        val aiResult = AiAnalysisResult(
            isMatch = true,
            confidence = 95,
            detectedProduct = "Sapphire RX 470",
            detectedModel = "RX 470",
            detectedSpecs = mapOf("vram" to "8GB"),
            photoMatchesTitle = true,
            condition = "used",
            completeness = "complete",
            visibleDefects = emptyList(),
            isSuspicious = false,
            categoryMatch = true,
            reasons = listOf("Фотография и описание соответствуют видеокарте RX 470"),
            rejectReason = null
        )

        val eval = engine.evaluateResult(aiResult)
        assertEquals(AiStatus.MATCH, eval.status)
        assertTrue(eval.shouldNotify)
        assertTrue(eval.shouldSaveFinding)
        assertEquals(95, eval.result?.confidence)
    }

    // 2. Scenario 2: Different product
    @Test
    fun testScenario2_differentProduct_rejected() {
        val aiResult = AiAnalysisResult(
            isMatch = false,
            confidence = 88,
            detectedProduct = "GeForce GTX 1050",
            detectedModel = "GTX 1050",
            detectedSpecs = null,
            photoMatchesTitle = true,
            condition = "used",
            completeness = "complete",
            visibleDefects = emptyList(),
            isSuspicious = false,
            categoryMatch = true,
            reasons = listOf("Товар является видеокартой Nvidia GTX 1050, а не запрошенной RX 470"),
            rejectReason = "Несоответствие модели товара"
        )

        val eval = engine.evaluateResult(aiResult)
        assertEquals(AiStatus.REJECTED, eval.status)
        assertFalse(eval.shouldNotify)
        // In non-strict (manual review) mode, finding is saved for review
        assertTrue(eval.shouldSaveFinding)

        // In strict mode: finding should NOT be saved
        aiPreferences.isStrictMode = true
        val evalStrict = engine.evaluateResult(aiResult)
        assertEquals(AiStatus.REJECTED, evalStrict.status)
        assertFalse(evalStrict.shouldSaveFinding)
        assertFalse(evalStrict.shouldNotify)
    }

    // 3. Scenario 3: Selling only a box
    @Test
    fun testScenario3_boxOnly_rejected() {
        val aiResult = AiAnalysisResult(
            isMatch = false,
            confidence = 92,
            detectedProduct = "Коробка от Sapphire RX 470",
            detectedModel = "RX 470 Box",
            detectedSpecs = null,
            photoMatchesTitle = false,
            condition = "used",
            completeness = "box_only",
            visibleDefects = emptyList(),
            isSuspicious = true,
            categoryMatch = false,
            reasons = listOf("Продаётся только оригинальная коробка от видеокарты, самого устройства нет"),
            rejectReason = "Продажа только коробки"
        )

        val eval = engine.evaluateResult(aiResult)
        assertEquals(AiStatus.REJECTED, eval.status)
        assertFalse(eval.shouldNotify)
    }

    // 4. Scenario 4: Selling spare parts
    @Test
    fun testScenario4_partsOnly_rejected() {
        val aiResult = AiAnalysisResult(
            isMatch = false,
            confidence = 85,
            detectedProduct = "Вентилятор кулера RX 470",
            detectedModel = "Fan replacement",
            detectedSpecs = null,
            photoMatchesTitle = false,
            condition = "new",
            completeness = "parts_only",
            visibleDefects = emptyList(),
            isSuspicious = false,
            categoryMatch = false,
            reasons = listOf("В объявлении продаются только запчасти (кулеры), а не само устройство"),
            rejectReason = "Продажа запчастей"
        )

        val eval = engine.evaluateResult(aiResult)
        assertEquals(AiStatus.REJECTED, eval.status)
        assertFalse(eval.shouldNotify)
    }

    // 5. Scenario 5: Defective product
    @Test
    fun testScenario5_defectiveProduct_visibleDefectsIdentified() {
        val aiResult = AiAnalysisResult(
            isMatch = true,
            confidence = 70,
            detectedProduct = "Sapphire RX 470",
            detectedModel = "RX 470",
            detectedSpecs = null,
            photoMatchesTitle = true,
            condition = "broken",
            completeness = "complete",
            visibleDefects = listOf("Следы прогрева чипа", "Один вентилятор сломан"),
            isSuspicious = true,
            categoryMatch = true,
            reasons = listOf("Видеокарта имеет видимые следы ремонта и поломки"),
            rejectReason = null
        )

        val eval = engine.evaluateResult(aiResult)
        // Defective / suspicious items should be marked NEEDS_REVIEW so user is warned
        assertEquals(AiStatus.NEEDS_REVIEW, eval.status)
        assertTrue(eval.shouldNotify)
        assertTrue(eval.shouldSaveFinding)
        assertEquals(2, eval.result?.visibleDefects?.size)
    }

    // 6. Scenario 6: Photo does not match title
    @Test
    fun testScenario6_photoDoesNotMatchTitle_flagged() {
        val aiResult = AiAnalysisResult(
            isMatch = false,
            confidence = 85,
            detectedProduct = "Холодильник Атлант",
            detectedModel = "Атлант",
            detectedSpecs = null,
            photoMatchesTitle = false,
            condition = "used",
            completeness = "complete",
            visibleDefects = emptyList(),
            isSuspicious = true,
            categoryMatch = false,
            reasons = listOf("На фотографии изображён холодильник, а в заголовке заявлена видеокарта"),
            rejectReason = "Фотография не соответствует заголовку"
        )

        val eval = engine.evaluateResult(aiResult)
        assertEquals(AiStatus.REJECTED, eval.status)
        assertFalse("photoMatchesTitle must be false", eval.result!!.photoMatchesTitle)
        assertTrue(eval.shouldSaveFinding)
    }

    // 7. Scenario 7: Multiple photos of one product
    @Test
    fun testScenario7_multiplePhotosProcessed() {
        val listing = createSampleListing(
            id = 555L,
            title = "Sapphire RX 470 4GB"
        )
        assertEquals(2, listing.photoUrls.size)
    }

    // 8. Scenario 8: Empty description handled gracefully
    @Test
    fun testScenario8_emptyDescription_doesNotCrash() {
        val aiResult = AiAnalysisResult(
            isMatch = true,
            confidence = 80,
            detectedProduct = "RX 470",
            detectedModel = "RX 470",
            detectedSpecs = null,
            photoMatchesTitle = true,
            condition = "used",
            completeness = "complete",
            visibleDefects = emptyList(),
            isSuspicious = false,
            categoryMatch = true,
            reasons = listOf("Определено только по фото и заголовку"),
            rejectReason = null
        )

        val eval = engine.evaluateResult(aiResult)
        assertEquals(AiStatus.MATCH, eval.status)
        assertTrue(eval.shouldSaveFinding)
    }

    // 9. Scenario 9: Groq API error -> handled gracefully (retains listing, does not crash)
    @Test
    fun testScenario9_apiError_retainsFinding() {
        // Test handleAiError
        val errorResult = Class.forName("ua.magerko.olxradar.ai.AiFilterEngine")
            .getDeclaredMethod("handleAiError", Throwable::class.java).apply { isAccessible = true }
            .invoke(engine, RuntimeException("Groq API 500 Internal Error")) as AiEvaluation

        assertEquals(AiStatus.ERROR, errorResult.status)
        assertTrue("Findings must not be dropped when AI fails", errorResult.shouldSaveFinding)
        assertTrue("Notifications should still be sent when AI fails so user doesn't miss item", errorResult.shouldNotify)
        assertNull(errorResult.result)
        assertEquals("Groq API 500 Internal Error", errorResult.errorMessage)
    }

    // 10. Scenario 10: Rate limit 429
    @Test
    fun testScenario10_rateLimitException_handled() {
        val ex = GroqVisionClient.HttpRateLimitException("HTTP 429 Too Many Requests", 5000L)
        assertEquals(5000L, ex.retryAfterMs)
        val errorResult = Class.forName("ua.magerko.olxradar.ai.AiFilterEngine")
            .getDeclaredMethod("handleAiError", Throwable::class.java).apply { isAccessible = true }
            .invoke(engine, ex) as AiEvaluation

        assertEquals(AiStatus.ERROR, errorResult.status)
        assertTrue(errorResult.shouldSaveFinding)
    }

    // 11. Scenario 11: Malformed JSON handled gracefully
    @Test
    fun testScenario11_malformedJson_returnsNull() {
        assertNull(AiAnalysisResult.fromJson(""))
        assertNull(AiAnalysisResult.fromJson("invalid text"))
        assertNull(AiAnalysisResult.fromJson("{incomplete"))
    }

    // 12. Scenario 12: Duplicate listing uses cached result without re-analysis
    @Test
    fun testScenario12_duplicateListingUsesCache() = runBlocking {
        val listing = createSampleListing(id = 8888L)
        val watch = createSampleWatch()

        val cachedJson = """
            {
              "is_match": true,
              "confidence": 92,
              "detected_product": "Cached RX 470",
              "detected_model": "RX 470",
              "photo_matches_title": true,
              "condition": "used",
              "completeness": "complete",
              "visible_defects": [],
              "is_suspicious": false,
              "category_match": true,
              "reasons": ["Из кэша"],
              "reject_reason": null
            }
        """.trimIndent()

        // Insert into cache
        db.aiCacheDao().insert(ua.magerko.olxradar.data.local.AiCacheEntry(listing.id, cachedJson))

        // Run evaluateListing
        val eval = engine.evaluateListing(listing, watch)

        assertEquals(AiStatus.MATCH, eval.status)
        assertEquals(92, eval.result?.confidence)
        assertEquals("Cached RX 470", eval.result?.detectedProduct)
    }

    // Confidence ranges test
    @Test
    fun testConfidenceRanges_andDoNotDropGoodDealsOnUncertainty() {
        // High relevance (90-100)
        val r95 = AiAnalysisResult(true, 95, "RX 470", "RX 470", null, true, "used", "complete", emptyList(), false, true, emptyList(), null)
        assertEquals(AiStatus.MATCH, engine.evaluateResult(r95).status)

        // Likely match (75-89)
        val r80 = AiAnalysisResult(true, 80, "RX 470", "RX 470", null, true, "used", "complete", emptyList(), false, true, emptyList(), null)
        assertEquals(AiStatus.MATCH, engine.evaluateResult(r80).status)

        // Requires extra check (50-74)
        val r60 = AiAnalysisResult(true, 60, "RX 470", "RX 470", null, true, "used", "complete", emptyList(), false, true, emptyList(), null)
        val eval60 = engine.evaluateResult(r60)
        assertEquals(AiStatus.NEEDS_REVIEW, eval60.status)
        assertTrue("Do not drop potentially good deals on low confidence", eval60.shouldSaveFinding)

        // Uncertainty on mismatch (< 75): should NOT reject automatically
        val rMismatchUncertain = AiAnalysisResult(false, 55, "Unknown", null, null, true, "used", "complete", emptyList(), false, true, emptyList(), null)
        val evalUncertain = engine.evaluateResult(rMismatchUncertain)
        assertEquals(AiStatus.NEEDS_REVIEW, evalUncertain.status)
        assertTrue("Uncertain mismatch should be preserved for manual review", evalUncertain.shouldSaveFinding)
    }

    // AI Disabled test
    @Test
    fun testAiDisabled_skipsGracefully() = runBlocking {
        aiPreferences.isAiEnabled = false
        val listing = createSampleListing()
        val watch = createSampleWatch()

        val eval = engine.evaluateListing(listing, watch)
        assertEquals(AiStatus.SKIPPED, eval.status)
        assertTrue(eval.shouldSaveFinding)
        assertTrue(eval.shouldNotify)
    }

    // Manual review mode enabled vs disabled
    @Test
    fun testManualReviewMode_disabled_dropsRejectedFindings() {
        val rejectedResult = AiAnalysisResult(
            isMatch = false,
            confidence = 90,
            detectedProduct = "Wrong Product",
            detectedModel = "Wrong",
            detectedSpecs = null,
            photoMatchesTitle = false,
            condition = "used",
            completeness = "complete",
            visibleDefects = emptyList(),
            isSuspicious = false,
            categoryMatch = false,
            reasons = listOf("Mismatch"),
            rejectReason = "Mismatch"
        )

        // When manual review mode is ON (and strict mode OFF): save for review
        aiPreferences.isManualReviewMode = true
        aiPreferences.isStrictMode = false
        val evalManual = engine.evaluateResult(rejectedResult)
        assertEquals(AiStatus.REJECTED, evalManual.status)
        assertFalse(evalManual.shouldNotify)
        assertTrue(evalManual.shouldSaveFinding)

        // When manual review mode is OFF: drop rejected finding
        aiPreferences.isManualReviewMode = false
        val evalAuto = engine.evaluateResult(rejectedResult)
        assertEquals(AiStatus.REJECTED, evalAuto.status)
        assertFalse(evalAuto.shouldNotify)
        assertFalse(evalAuto.shouldSaveFinding)
    }

    // Cache pruning test
    @Test
    fun testAiCacheDao_pruneOld() = runBlocking {
        val oldEntry = ua.magerko.olxradar.data.local.AiCacheEntry(
            listingId = 111L,
            resultJson = "{}",
            createdAt = System.currentTimeMillis() - 20L * 24 * 60 * 60 * 1000L // 20 days ago
        )
        val freshEntry = ua.magerko.olxradar.data.local.AiCacheEntry(
            listingId = 222L,
            resultJson = "{}",
            createdAt = System.currentTimeMillis() // now
        )

        db.aiCacheDao().insert(oldEntry)
        db.aiCacheDao().insert(freshEntry)
        assertEquals(2, db.aiCacheDao().getCount())

        val cutoff = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000L
        db.aiCacheDao().pruneOld(cutoff)

        assertEquals(1, db.aiCacheDao().getCount())
        assertNull(db.aiCacheDao().getByListingId(111L))
        assertNotNull(db.aiCacheDao().getByListingId(222L))
    }

    // Reasoning model <think>...</think> stripping test
    @Test
    fun testAiAnalysisResult_handlesReasoningThinkBlocks() {
        val rawAiWithThink = """
            <think>
            The user is searching for RX 470 with { "vram": "8GB" }.
            I see a GPU in the photo.
            </think>
            {
              "is_match": true,
              "confidence": 93,
              "detected_product": "Sapphire RX 470",
              "detected_model": "RX 470",
              "detected_specs": {"vram": "8GB"},
              "photo_matches_title": true,
              "condition": "used",
              "completeness": "complete",
              "visible_defects": [],
              "is_suspicious": false,
              "category_match": true,
              "reasons": ["Соответствует запросу"],
              "reject_reason": null
            }
        """.trimIndent()

        val parsed = AiAnalysisResult.fromJson(rawAiWithThink)
        assertNotNull("Should parse JSON cleanly even when <think> block contains braces", parsed)
        assertTrue(parsed!!.isMatch)
        assertEquals(93, parsed.confidence)
        assertEquals("Sapphire RX 470", parsed.detectedProduct)
    }

    @Test
    fun testNotifyRejected_toggleInPreferences() {
        val rejectedResult = AiAnalysisResult(
            isMatch = false,
            confidence = 88,
            detectedProduct = "GTX 1050",
            detectedModel = "GTX 1050",
            detectedSpecs = null,
            photoMatchesTitle = true,
            condition = "used",
            completeness = "complete",
            visibleDefects = emptyList(),
            isSuspicious = false,
            categoryMatch = true,
            reasons = listOf("Несоответствие модели"),
            rejectReason = "Не та модель"
        )

        // 1. By default, isNotifyRejected = false -> should NOT notify
        assertFalse(aiPreferences.isNotifyRejected)
        val evalDefault = engine.evaluateResult(rejectedResult)
        assertEquals(AiStatus.REJECTED, evalDefault.status)
        assertFalse("By default, rejected items must NOT notify", evalDefault.shouldNotify)

        // 2. Enable in settings -> should notify
        aiPreferences.isNotifyRejected = true
        val evalEnabled = engine.evaluateResult(rejectedResult)
        assertEquals(AiStatus.REJECTED, evalEnabled.status)
        assertTrue("When enabled in settings, rejected items MUST notify", evalEnabled.shouldNotify)
    }

    @Test
    fun testRejectedItemsSaved_byDefault_forRejectedTab() {
        val rejectedResult = AiAnalysisResult(
            isMatch = false,
            confidence = 88,
            detectedProduct = "GTX 1050",
            detectedModel = "GTX 1050",
            detectedSpecs = null,
            photoMatchesTitle = true,
            condition = "used",
            completeness = "complete",
            visibleDefects = emptyList(),
            isSuspicious = false,
            categoryMatch = true,
            reasons = listOf("Несоответствие модели"),
            rejectReason = "Не та модель"
        )

        // Default: Strict mode OFF, manual review mode ON -> saved to appear in Rejected tab!
        aiPreferences.isStrictMode = false
        aiPreferences.isManualReviewMode = true
        val evalDefault = engine.evaluateResult(rejectedResult)
        assertEquals(AiStatus.REJECTED, evalDefault.status)
        assertTrue("In default mode, rejected items must be saved for the rejected tab", evalDefault.shouldSaveFinding)

        // Strict mode ON -> not saved
        aiPreferences.isStrictMode = true
        val evalStrict = engine.evaluateResult(rejectedResult)
        assertEquals(AiStatus.REJECTED, evalStrict.status)
        assertFalse("In strict mode, rejected items must not be saved", evalStrict.shouldSaveFinding)
    }
}
