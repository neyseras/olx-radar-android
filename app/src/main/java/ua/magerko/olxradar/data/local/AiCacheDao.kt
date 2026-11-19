package ua.magerko.olxradar.data.local

import androidx.room.*

@Entity(tableName = "ai_cache")
data class AiCacheEntry(
    @PrimaryKey
    @ColumnInfo(name = "listing_id")
    val listingId: Long,
    @ColumnInfo(name = "result_json")
    val resultJson: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface AiCacheDao {

    @Query("SELECT * FROM ai_cache WHERE listing_id = :listingId")
    suspend fun getByListingId(listingId: Long): AiCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AiCacheEntry)

    @Query("DELETE FROM ai_cache WHERE created_at < :olderThan")
    suspend fun pruneOld(olderThan: Long)

    @Query("DELETE FROM ai_cache")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM ai_cache")
    suspend fun getCount(): Int
}
