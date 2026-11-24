package ua.magerko.olxradar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ua.magerko.olxradar.data.model.SellerBlacklist

@Dao
interface SellerBlacklistDao {

    @Query("SELECT * FROM seller_blacklist ORDER BY added_at DESC")
    fun getAllFlow(): Flow<List<SellerBlacklist>>

    @Query("SELECT * FROM seller_blacklist ORDER BY added_at DESC")
    suspend fun getAll(): List<SellerBlacklist>

    @Query("SELECT seller_key FROM seller_blacklist")
    fun getBlockedKeysFlow(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM seller_blacklist WHERE seller_key = :sellerKey)")
    suspend fun isBlocked(sellerKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SellerBlacklist)

    @Query("DELETE FROM seller_blacklist WHERE seller_key = :sellerKey")
    suspend fun deleteByKey(sellerKey: String)

    @Query("DELETE FROM seller_blacklist")
    suspend fun clearAll()
}
