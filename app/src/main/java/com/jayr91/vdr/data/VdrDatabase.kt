package com.jayr91.vdr.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val displayName: String,
    val category: String,
    val destPath: String,
    val contentUri: String? = null,
    val status: String,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val error: String,
    val numSegments: Int,
    val createdAt: Long,
    val scheduledAt: Long?,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>
}

@Database(entities = [DownloadEntity::class], version = 2, exportSchema = false)
abstract class VdrDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao

    companion object {
        @Volatile private var instance: VdrDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN contentUri TEXT")
            }
        }

        fun get(context: Context): VdrDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, VdrDatabase::class.java, "vdr.db")
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
