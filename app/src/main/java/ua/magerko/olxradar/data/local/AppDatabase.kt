package ua.magerko.olxradar.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.SeenItem
import ua.magerko.olxradar.data.model.Watch

@Database(
    entities = [Watch::class, Finding::class, SeenItem::class, AiCacheEntry::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchDao(): WatchDao
    abstract fun findingDao(): FindingDao
    abstract fun seenItemDao(): SeenItemDao
    abstract fun aiCacheDao(): AiCacheDao

    companion object {
        const val DATABASE_NAME = "olx_radar.db"
        const val DATABASE_VERSION = 7

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
