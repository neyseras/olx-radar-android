package ua.magerko.olxradar.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.WatchPrice

data class WatchFindingsCount(
    @ColumnInfo(name = "watch_id") val watchId: Long,
    @ColumnInfo(name = "count") val count: Int
)

@Dao
interface FindingDao {

    @Query("SELECT watch_id, price FROM findings WHERE price IS NOT NULL AND price > 0 AND ((ai_status != 'rejected' AND ai_status != 'queued') OR ai_status IS NULL)")
    fun getAllValidPricesFlow(): Flow<List<WatchPrice>>

    @Query("SELECT price FROM findings WHERE watch_id = :watchId AND price IS NOT NULL AND price > 0 AND ((ai_status != 'rejected' AND ai_status != 'queued') OR ai_status IS NULL)")
    fun getPricesForWatchFlow(watchId: Long): Flow<List<Int>>

    @Query("SELECT watch_id, COUNT(*) as count FROM findings WHERE (ai_status != 'rejected' AND ai_status != 'queued') OR ai_status IS NULL GROUP BY watch_id")
    fun getFindingsCountsFlow(): Flow<List<WatchFindingsCount>>

    @Query("SELECT COUNT(*) FROM findings WHERE (ai_status != 'rejected' AND ai_status != 'queued') OR ai_status IS NULL")
    fun getActiveFindingsCountFlow(): Flow<Int>

    @Query("SELECT * FROM findings ORDER BY found_at DESC LIMIT :limit")
    fun getAllFindingsFlow(limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE watch_id = :watchId ORDER BY found_at DESC LIMIT :limit")
    fun getFindingsByWatchFlow(watchId: Long, limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE (ai_status != 'rejected' AND ai_status != 'queued') OR ai_status IS NULL ORDER BY found_at DESC LIMIT :limit")
    fun getActiveFindingsFlow(limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE ((ai_status != 'rejected' AND ai_status != 'queued') OR ai_status IS NULL) AND watch_id = :watchId ORDER BY found_at DESC LIMIT :limit")
    fun getActiveFindingsByWatchFlow(watchId: Long, limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE ai_status = 'rejected' ORDER BY found_at DESC LIMIT :limit")
    fun getRejectedFindingsFlow(limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE ai_status = 'rejected' AND watch_id = :watchId ORDER BY found_at DESC LIMIT :limit")
    fun getRejectedFindingsByWatchFlow(watchId: Long, limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE ai_status = 'queued' ORDER BY found_at DESC LIMIT :limit")
    fun getQueuedFindingsFlow(limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE ai_status = 'queued' AND watch_id = :watchId ORDER BY found_at DESC LIMIT :limit")
    fun getQueuedFindingsByWatchFlow(watchId: Long, limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT COUNT(*) FROM findings WHERE ai_status = 'queued'")
    fun getQueuedFindingsCountFlow(): Flow<Int>

    @Query("SELECT * FROM findings ORDER BY found_at DESC LIMIT :limit")
    suspend fun getAllFindings(limit: Int = 100): List<Finding>

    @Query("SELECT * FROM findings WHERE id = :id")
    suspend fun getFindingById(id: Long): Finding?

    @Query("SELECT * FROM findings WHERE listing_id = :listingId LIMIT 1")
    suspend fun getFindingByListingId(listingId: Long): Finding?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinding(finding: Finding): Long

    @Query("DELETE FROM findings WHERE id = :id")
    suspend fun deleteFinding(id: Long)

    @Query("DELETE FROM findings")
    suspend fun clearAllFindings()

    @Query("DELETE FROM findings WHERE ai_status = 'rejected'")
    suspend fun clearRejectedFindings()

    @Query("DELETE FROM findings WHERE ai_status = 'queued'")
    suspend fun clearQueuedFindings()

    @Query("UPDATE findings SET is_read = :isRead WHERE id = :id")
    suspend fun markAsRead(id: Long, isRead: Boolean = true)

    @Query("SELECT COUNT(*) FROM findings WHERE is_read = 0 AND ((ai_status != 'rejected' AND ai_status != 'queued') OR ai_status IS NULL)")
    fun getUnreadCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM findings WHERE watch_id = :watchId AND ((ai_status != 'rejected' AND ai_status != 'queued') OR ai_status IS NULL)")
    fun getFindingsCountByWatchFlow(watchId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM findings WHERE watch_id = :watchId AND ((ai_status != 'rejected' AND ai_status != 'queued') OR ai_status IS NULL)")
    suspend fun getFindingsCountByWatch(watchId: Long): Int

    @Query("DELETE FROM findings WHERE watch_id = :watchId")
    suspend fun deleteByWatchId(watchId: Long)

    @Update
    suspend fun updateFinding(finding: Finding)
}
