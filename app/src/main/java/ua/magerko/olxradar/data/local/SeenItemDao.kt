package ua.magerko.olxradar.data.local

import androidx.room.*
import ua.magerko.olxradar.data.model.SeenItem

@Dao
abstract class SeenItemDao {

    @Query("SELECT listing_id FROM seen_items WHERE watch_id = :watchId")
    abstract suspend fun getSeenIds(watchId: Long): List<Long>

    @Query("SELECT last_pushed_at FROM seen_items WHERE watch_id = :watchId AND listing_id = :listingId")
    abstract suspend fun getLastPushedAt(watchId: Long, listingId: Long): Long?

    @Query("SELECT * FROM seen_items WHERE watch_id = :watchId AND listing_id = :listingId")
    abstract suspend fun getSeenItem(watchId: Long, listingId: Long): SeenItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrReplace(seenItem: SeenItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrReplaceAll(items: List<SeenItem>)

    /**
     * A4: Prunes seen_items so that at most [limit] latest items per watch are kept.
     */
    @Query("""
        DELETE FROM seen_items 
        WHERE watch_id = :watchId 
          AND listing_id NOT IN (
              SELECT listing_id FROM seen_items 
              WHERE watch_id = :watchId 
              ORDER BY added_at DESC 
              LIMIT :limit
          )
    """)
    abstract suspend fun pruneOldSeenItems(watchId: Long, limit: Int = 2000)

    @Query("DELETE FROM seen_items WHERE watch_id = :watchId")
    abstract suspend fun deleteByWatchId(watchId: Long)

    @Transaction
    open suspend fun markSeenAndPrune(
        watchId: Long,
        listingId: Long,
        lastPushedAt: Long?,
        lastPrice: Int? = null,
        lastCurrency: String? = null,
        maxLimit: Int = 2000
    ) {
        val prev = if (lastPrice == null) getSeenItem(watchId, listingId) else null
        insertOrReplace(
            SeenItem(
                watchId = watchId,
                listingId = listingId,
                lastPushedAt = lastPushedAt,
                lastPrice = lastPrice ?: prev?.lastPrice,
                lastCurrency = if (lastPrice != null) lastCurrency else prev?.lastCurrency,
                addedAt = System.currentTimeMillis()
            )
        )
        pruneOldSeenItems(watchId, maxLimit)
    }

    @Transaction
    open suspend fun markSeenBatchAndPrune(
        watchId: Long,
        items: List<SeenItem>,
        maxLimit: Int = 2000
    ) {
        if (items.isEmpty()) return
        insertOrReplaceAll(items)
        pruneOldSeenItems(watchId, maxLimit)
    }
}
