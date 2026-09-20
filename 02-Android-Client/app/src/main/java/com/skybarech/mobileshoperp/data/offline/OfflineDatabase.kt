package com.skybarech.mobileshoperp.data.offline

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_snapshot")
data class LocalSnapshotEntity(
    @androidx.room.PrimaryKey val id: Int = 1,
    val payloadJson: String,
    val updatedAt: Long
)

@Entity(tableName = "sync_outbox")
data class OutboxEntity(
    @androidx.room.PrimaryKey val operationId: String,
    val entityType: String,
    val entityId: String,
    val actionName: String = "upsert",
    val payloadJson: String,
    val status: String = "pending",
    val attempts: Int = 0,
    val lastError: String? = null,
    val nextRetryAt: Long = 0,
    val createdAt: Long,
    val syncedAt: Long? = null
)

@Entity(tableName = "sync_config")
data class SyncConfigEntity(
    @androidx.room.PrimaryKey val id: Int = 1,
    val apiBaseUrl: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val deviceId: String,
    val pullCursor: Long = 0,
    val lastSyncAt: Long? = null,
    val lastSyncError: String? = null
)

@Dao
interface OfflineDao {
    @Query("SELECT * FROM local_snapshot WHERE id = 1")
    suspend fun snapshot(): LocalSnapshotEntity?

    @Query("SELECT * FROM local_snapshot WHERE id = 1")
    fun observeSnapshot(): Flow<LocalSnapshotEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSnapshot(snapshot: LocalSnapshotEntity)

    @Query("DELETE FROM local_snapshot")
    suspend fun clearSnapshot()

    @Query("DELETE FROM sync_outbox")
    suspend fun clearOutbox()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addOperation(operation: OutboxEntity): Long

    @Query("SELECT * FROM sync_outbox WHERE status IN ('pending', 'failed', 'syncing') AND nextRetryAt <= :now ORDER BY createdAt LIMIT :limit")
    suspend fun readyOperations(now: Long, limit: Int): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status IN ('pending', 'failed', 'syncing')")
    suspend fun pendingCount(): Int

    @Query("SELECT * FROM sync_outbox WHERE status IN ('pending', 'failed', 'syncing') ORDER BY createdAt, rowid")
    suspend fun unsentOperations(): List<OutboxEntity>

    @Query("UPDATE sync_outbox SET nextRetryAt = 0 WHERE status = 'failed'")
    suspend fun retryFailuresNow()

    @Query("UPDATE sync_outbox SET status = 'syncing', attempts = attempts + 1, lastError = NULL WHERE operationId IN (:ids)")
    suspend fun markSyncing(ids: List<String>)

    @Query("UPDATE sync_outbox SET status = 'synced', syncedAt = :now, lastError = NULL WHERE operationId IN (:ids)")
    suspend fun markSynced(ids: List<String>, now: Long)

    @Query("UPDATE sync_outbox SET status = 'failed', lastError = :error, nextRetryAt = :nextRetryAt WHERE operationId = :id")
    suspend fun markFailed(id: String, error: String, nextRetryAt: Long)

    @Query("SELECT * FROM sync_config WHERE id = 1")
    suspend fun config(): SyncConfigEntity?

    @Query("SELECT * FROM sync_config WHERE id = 1")
    fun observeConfig(): Flow<SyncConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: SyncConfigEntity)

    @Query("UPDATE sync_config SET lastSyncAt = :now, lastSyncError = NULL WHERE id = 1")
    suspend fun syncSucceeded(now: Long)

    @Query("UPDATE sync_config SET pullCursor = :cursor, lastSyncAt = :now, lastSyncError = NULL WHERE id = 1")
    suspend fun pullSucceeded(cursor: Long, now: Long)

    @Query("UPDATE sync_config SET lastSyncError = :error WHERE id = 1")
    suspend fun syncFailed(error: String)
}

@Database(
    entities = [LocalSnapshotEntity::class, OutboxEntity::class, SyncConfigEntity::class],
    version = 1,
    exportSchema = true
)
abstract class SkyBarechDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        @Volatile private var instance: SkyBarechDatabase? = null

        fun get(context: Context): SkyBarechDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SkyBarechDatabase::class.java,
                "skybarech-local.db"
            ).build().also { instance = it }
        }
    }
}
