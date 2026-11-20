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
        val aiPreferences = ua.magerko.olxradar.ai.AiPreferences(context)
        val aiFilterEngine = ua.magerko.olxradar.ai.AiFilterEngine(
            aiPreferences,
            ua.magerko.olxradar.ai.GroqVisionClient(aiPreferences),
            ua.magerko.olxradar.ai.AiImageProcessor(),
            db.aiCacheDao()
        )
        viewModel = FindingsViewModel(db.findingDao(), db.watchDao(), aiFilterEngine)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testFindingsViewModel_tabStateAndFilterState() {
        assertEquals(FindingsFeedTab.MATCHES, viewModel.selectedFeedTabState.value)
        assertFalse(viewModel.isRejectedTabState.value)
        assertNull(viewModel.selectedWatchIdState.value)

        viewModel.setRejectedTab(true)
        assertEquals(FindingsFeedTab.REJECTED, viewModel.selectedFeedTabState.value)
        assertTrue(viewModel.isRejectedTabState.value)

        viewModel.setFeedTab(FindingsFeedTab.QUEUED)
        assertEquals(FindingsFeedTab.QUEUED, viewModel.selectedFeedTabState.value)
        assertFalse(viewModel.isRejectedTabState.value)

        viewModel.setRejectedTab(false)
        assertEquals(FindingsFeedTab.MATCHES, viewModel.selectedFeedTabState.value)
        assertFalse(viewModel.isRejectedTabState.value)

        viewModel.filterByWatch(42L)
        assertEquals(42L, viewModel.selectedWatchIdState.value)

        viewModel.filterByWatch(null)
        assertNull(viewModel.selectedWatchIdState.value)
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
