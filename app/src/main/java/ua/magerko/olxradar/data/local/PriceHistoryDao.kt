package ua.magerko.olxradar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ua.magerko.olxradar.data.model.PriceHistory

@Dao
interface PriceHistoryDao {

    @Query("SELECT * FROM price_history WHERE listing_id = :listingId ORDER BY recorded_at ASC")
    fun getHistoryForListingFlow(listingId: Long): Flow<List<PriceHistory>>

    @Query("SELECT * FROM price_history WHERE listing_id = :listingId ORDER BY recorded_at DESC LIMIT 1")
    suspend fun getLatestForListing(listingId: Long): PriceHistory?

    @Query("SELECT * FROM price_history ORDER BY recorded_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 500): List<PriceHistory>

    @Insert
    suspend fun insert(entry: PriceHistory)

    @Query("DELETE FROM price_history")
    suspend fun clearAll()
}
