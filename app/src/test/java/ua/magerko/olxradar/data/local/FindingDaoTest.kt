package ua.magerko.olxradar.data.local

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
import ua.magerko.olxradar.ai.AiStatus
import ua.magerko.olxradar.data.model.Finding

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FindingDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var findingDao: FindingDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        findingDao = db.findingDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testActiveAndRejectedFindings_separatedProperly() = runBlocking {
        // 1. Insert an active match finding
        val id101 = findingDao.insertFinding(
            Finding(
                watchId = 1L,
                listingId = 101L,
                title = "Sapphire RX 580 8GB",
                description = "Great GPU in mint condition",
                price = 3500,
                currency = "UAH",
                cityName = "Kyiv",
                url = "https://olx.ua/d/101",
                photoUrl = "https://img.olx.ua/101.jpg",
                reason = "new",
                createdAt = 1000L,
                foundAt = 1000L,
                isRead = false,
                aiScore = 95,
                aiStatus = AiStatus.MATCH
            )
        )

        // 2. Insert an active needs-review finding
        findingDao.insertFinding(
            Finding(
                watchId = 1L,
                listingId = 102L,
                title = "RX 580 condition uncertain",
                description = "For parts or repair",
                price = 2000,
                currency = "UAH",
                cityName = "Lviv",
                url = "https://olx.ua/d/102",
                photoUrl = null,
                reason = "new",
                createdAt = 1100L,
                foundAt = 1100L,
                isRead = false,
                aiScore = 60,
                aiStatus = AiStatus.NEEDS_REVIEW
            )
        )

        // 3. Insert a rejected finding
        findingDao.insertFinding(
            Finding(
                watchId = 1L,
                listingId = 103L,
                title = "Box only Sapphire RX 580",
                description = "Only empty cardboard box",
                price = 300,
                currency = "UAH",
                cityName = "Odesa",
                url = "https://olx.ua/d/103",
                photoUrl = null,
                reason = "new",
                createdAt = 1200L,
                foundAt = 1200L,
                isRead = false,
                aiScore = 15,
                aiStatus = AiStatus.REJECTED,
                aiRejectReason = "Продажа только коробки"
            )
        )

        // 4. Insert an active finding with null ai_status
        findingDao.insertFinding(
            Finding(
                watchId = 2L,
                listingId = 201L,
                title = "PlayStation 5",
                description = "Standard PS5 console",
                price = 18000,
                currency = "UAH",
                cityName = "Dnipro",
                url = "https://olx.ua/d/201",
                photoUrl = null,
                reason = "new",
                createdAt = 1300L,
                foundAt = 1300L,
                isRead = false,
                aiStatus = null
            )
        )

        // Verify active findings flow contains only non-rejected items (101, 102, 201)
        val active = findingDao.getActiveFindingsFlow().first()
        assertEquals(3, active.size)
        assertTrue(active.any { it.listingId == 101L })
        assertTrue(active.any { it.listingId == 102L })
        assertTrue(active.any { it.listingId == 201L })
        assertFalse(active.any { it.listingId == 103L })

        // Verify rejected findings flow contains ONLY 103
        val rejected = findingDao.getRejectedFindingsFlow().first()
        assertEquals(1, rejected.size)
        assertEquals(103L, rejected[0].listingId)
        assertEquals("Продажа только коробки", rejected[0].aiRejectReason)

        // Verify watch-filtered active flow
        val watch1Active = findingDao.getActiveFindingsByWatchFlow(1L).first()
        assertEquals(2, watch1Active.size)
        assertTrue(watch1Active.any { it.listingId == 101L })
        assertTrue(watch1Active.any { it.listingId == 102L })

        // Verify watch-filtered rejected flow
        val watch1Rejected = findingDao.getRejectedFindingsByWatchFlow(1L).first()
        assertEquals(1, watch1Rejected.size)
        assertEquals(103L, watch1Rejected[0].listingId)

        val watch2Rejected = findingDao.getRejectedFindingsByWatchFlow(2L).first()
        assertEquals(0, watch2Rejected.size)

        // Verify getFindingById
        val fetched101 = findingDao.getFindingById(id101)
        assertNotNull(fetched101)
        assertEquals("Sapphire RX 580 8GB", fetched101?.title)

        // Verify watch findings count excludes rejected 103 (watch 1 has 2 active, not 3)
        val watchCounts = findingDao.getFindingsCountsFlow().first().associate { it.watchId to it.count }
        assertEquals(2, watchCounts[1L])
        assertEquals(1, watchCounts[2L])
        assertEquals(2, findingDao.getFindingsCountByWatch(1L))
        assertEquals(2, findingDao.getFindingsCountByWatchFlow(1L).first())

        // Verify unread count only counts active unread items (101, 102, 201), excluding rejected 103!
        val unreadCount = findingDao.getUnreadCountFlow().first()
        assertEquals(3, unreadCount)

        // Mark 101 as read
        findingDao.markAsRead(id101, true)
        val unreadAfterRead = findingDao.getUnreadCountFlow().first()
        assertEquals(2, unreadAfterRead)

        // Clear rejected findings: should delete 103 and leave 101, 102, 201 intact
        findingDao.clearRejectedFindings()
        val rejectedAfterClear = findingDao.getRejectedFindingsFlow().first()
        assertTrue(rejectedAfterClear.isEmpty())

        val activeAfterClear = findingDao.getActiveFindingsFlow().first()
        assertEquals(3, activeAfterClear.size)
    }
}
