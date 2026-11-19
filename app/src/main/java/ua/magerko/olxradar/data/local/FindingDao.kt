package ua.magerko.olxradar.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ua.magerko.olxradar.data.model.Finding

data class WatchFindingsCount(
    @ColumnInfo(name = "watch_id") val watchId: Long,
    @ColumnInfo(name = "count") val count: Int
)

@Dao
interface FindingDao {

    @Query("SELECT watch_id, COUNT(*) as count FROM findings WHERE ai_status != 'rejected' OR ai_status IS NULL GROUP BY watch_id")
    fun getFindingsCountsFlow(): Flow<List<WatchFindingsCount>>

    @Query("SELECT COUNT(*) FROM findings WHERE ai_status != 'rejected' OR ai_status IS NULL")
    fun getActiveFindingsCountFlow(): Flow<Int>

    @Query("SELECT * FROM findings ORDER BY found_at DESC LIMIT :limit")
    fun getAllFindingsFlow(limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE watch_id = :watchId ORDER BY found_at DESC LIMIT :limit")
    fun getFindingsByWatchFlow(watchId: Long, limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE ai_status != 'rejected' OR ai_status IS NULL ORDER BY found_at DESC LIMIT :limit")
    fun getActiveFindingsFlow(limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE (ai_status != 'rejected' OR ai_status IS NULL) AND watch_id = :watchId ORDER BY found_at DESC LIMIT :limit")
    fun getActiveFindingsByWatchFlow(watchId: Long, limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE ai_status = 'rejected' ORDER BY found_at DESC LIMIT :limit")
    fun getRejectedFindingsFlow(limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE ai_status = 'rejected' AND watch_id = :watchId ORDER BY found_at DESC LIMIT :limit")
    fun getRejectedFindingsByWatchFlow(watchId: Long, limit: Int = 100): Flow<List<Finding>>

    @Query("SELECT * FROM findings ORDER BY found_at DESC LIMIT :limit")
    suspend fun getAllFindings(limit: Int = 100): List<Finding>

    @Query("SELECT * FROM findings WHERE id = :id")
    suspend fun getFindingById(id: Long): Finding?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinding(finding: Finding): Long

    @Query("DELETE FROM findings WHERE id = :id")
    suspend fun deleteFinding(id: Long)

    @Query("DELETE FROM findings")
    suspend fun clearAllFindings()

    @Query("DELETE FROM findings WHERE ai_status = 'rejected'")
    suspend fun clearRejectedFindings()

    @Query("UPDATE findings SET is_read = :isRead WHERE id = :id")
    suspend fun markAsRead(id: Long, isRead: Boolean = true)

    @Query("SELECT COUNT(*) FROM findings WHERE is_read = 0 AND (ai_status != 'rejected' OR ai_status IS NULL)")
    fun getUnreadCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM findings WHERE watch_id = :watchId AND (ai_status != 'rejected' OR ai_status IS NULL)")
    fun getFindingsCountByWatchFlow(watchId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM findings WHERE watch_id = :watchId AND (ai_status != 'rejected' OR ai_status IS NULL)")
    suspend fun getFindingsCountByWatch(watchId: Long): Int

    @Query("DELETE FROM findings WHERE watch_id = :watchId")
    suspend fun deleteByWatchId(watchId: Long)
}
