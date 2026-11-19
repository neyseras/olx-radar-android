package ua.magerko.olxradar.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.magerko.olxradar.R
import ua.magerko.olxradar.ai.AiStatus
import ua.magerko.olxradar.data.local.AppDatabase
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.Watch

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FindingsUiTest {

    private lateinit var db: AppDatabase
    private lateinit var viewModel: FindingsViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        viewModel = FindingsViewModel(db.findingDao(), db.watchDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testFindingsViewModel_tabStateAndFilterState() {
        assertFalse(viewModel.isRejectedTabState.value)
        assertNull(viewModel.selectedWatchIdState.value)

        viewModel.setRejectedTab(true)
        assertTrue(viewModel.isRejectedTabState.value)

        viewModel.setRejectedTab(false)
        assertFalse(viewModel.isRejectedTabState.value)

        viewModel.filterByWatch(42L)
        assertEquals(42L, viewModel.selectedWatchIdState.value)

        viewModel.filterByWatch(null)
        assertNull(viewModel.selectedWatchIdState.value)
    }

    @Test
    fun testFindingDetailsBottomSheetFragment_newInstanceBundlePacking() {
        val sampleFinding = Finding(
            id = 42L,
            watchId = 3L,
            listingId = 98765L,
            title = "Apple iPhone 15 Pro Max 256GB",
            description = "Never opened, brand new with official receipt",
            price = 45000,
            currency = "UAH",
            cityName = "Lviv",
            url = "https://www.olx.ua/d/98765",
            photoUrl = "https://img.olx.ua/iphone.jpg",
            reason = "pushup",
            createdAt = 1700000000000L,
            foundAt = 1700000005000L,
            isRead = false,
            aiScore = 98,
            aiStatus = AiStatus.MATCH,
            aiProduct = "Apple iPhone 15 Pro Max",
            aiModel = "A3106",
            aiPhotoMatch = true,
            aiReasons = "• Оригинальный смартфон Apple",
            aiRejectReason = null,
            aiDetailsJson = "{\"is_match\":true,\"confidence\":98}"
        )

        val fragment = FindingDetailsBottomSheetFragment.newInstance(sampleFinding)
        val args = fragment.arguments
        assertNotNull(args)

        assertEquals(42L, args!!.getLong("arg_id"))
        assertEquals(3L, args.getLong("arg_watch_id"))
        assertEquals(98765L, args.getLong("arg_listing_id"))
        assertEquals("Apple iPhone 15 Pro Max 256GB", args.getString("arg_title"))
        assertEquals("Never opened, brand new with official receipt", args.getString("arg_description"))
        assertEquals(45000, args.getInt("arg_price"))
        assertEquals("UAH", args.getString("arg_currency"))
        assertEquals("Lviv", args.getString("arg_city"))
        assertEquals("https://www.olx.ua/d/98765", args.getString("arg_url"))
        assertEquals("https://img.olx.ua/iphone.jpg", args.getString("arg_photo_url"))
        assertEquals("pushup", args.getString("arg_reason"))
        assertEquals(98, args.getInt("arg_ai_score"))
        assertEquals(AiStatus.MATCH, args.getString("arg_ai_status"))
        assertEquals("Apple iPhone 15 Pro Max", args.getString("arg_ai_product"))
        assertEquals(true, args.getBoolean("arg_ai_photo_match"))
    }

    @Test
    fun testFindingsViewModel_getFindingById() = runBlocking {
        val sampleFinding = Finding(
            id = 0L,
            watchId = 1L,
            listingId = 555L,
            title = "Test Finding",
            description = "Test description",
            price = 1000,
            currency = "UAH",
            cityName = "Kyiv",
            url = "https://olx.ua/555",
            photoUrl = null,
            reason = "new",
            createdAt = 1000L,
            foundAt = 1000L
        )
        val id = db.findingDao().insertFinding(sampleFinding)
        val fetched = viewModel.getFindingById(id)
        assertNotNull(fetched)
        assertEquals(555L, fetched?.listingId)
        assertEquals("Test Finding", fetched?.title)
        assertEquals("Test description", fetched?.description)
    }
}
