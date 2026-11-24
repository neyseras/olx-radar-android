package ua.magerko.olxradar.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ua.magerko.olxradar.data.model.Watch

@Dao
abstract class WatchDao {

    @Query("SELECT * FROM watches ORDER BY id DESC")
    abstract fun getAllWatchesFlow(): Flow<List<Watch>>

    @Query("SELECT * FROM watches ORDER BY id DESC")
    abstract suspend fun getAllWatches(): List<Watch>

    @Query("SELECT * FROM watches WHERE is_active = 1")
    abstract suspend fun getActiveWatches(): List<Watch>

    @Query("SELECT * FROM watches WHERE id = :id")
    abstract suspend fun getWatchById(id: Long): Watch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWatch(watch: Watch): Long

    @Update
    abstract suspend fun updateWatch(watch: Watch)

    @Query("UPDATE watches SET is_active = :isActive WHERE id = :id")
    abstract suspend fun setWatchActive(id: Long, isActive: Boolean)

    @Query("UPDATE watches SET initialized = :initialized WHERE id = :id")
    abstract suspend fun setInitialized(id: Long, initialized: Boolean)

    @Query("UPDATE watches SET city_id = :cityId WHERE id = :id")
    abstract suspend fun updateCityId(id: Long, cityId: Int)

    @Query("UPDATE watches SET last_success_at = :timestamp, last_error = NULL, last_http_code = 200 WHERE id = :id")
    abstract suspend fun updateSuccess(id: Long, timestamp: Long)

    @Query("UPDATE watches SET last_error = :error, last_http_code = :httpCode WHERE id = :id")
    abstract suspend fun updateError(id: Long, error: String?, httpCode: Int?)

    @Query("DELETE FROM watches WHERE id = :id")
    abstract suspend fun deleteWatchById(id: Long)

    @Query("DELETE FROM seen_items WHERE watch_id = :watchId")
    abstract suspend fun deleteSeenItemsByWatchId(watchId: Long)

    @Query("DELETE FROM findings WHERE watch_id = :watchId")
    abstract suspend fun deleteFindingsByWatchId(watchId: Long)

    /**
     * A3: Deleting a radar cascades to its findings and seen_items via transaction.
     */
    @Transaction
    open suspend fun deleteWatchCascade(watchId: Long) {
        deleteSeenItemsByWatchId(watchId)
        deleteFindingsByWatchId(watchId)
        deleteWatchById(watchId)
    }
}
