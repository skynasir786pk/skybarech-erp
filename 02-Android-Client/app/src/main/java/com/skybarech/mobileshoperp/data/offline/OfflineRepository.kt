package com.skybarech.mobileshoperp.data.offline

import android.content.Context
import android.provider.Settings
import androidx.room.withTransaction
import com.skybarech.mobileshoperp.BuildConfig
import com.skybarech.mobileshoperp.data.local.LocalData
import com.skybarech.mobileshoperp.data.local.LocalDataStore
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.util.UUID

class OfflineRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = SkyBarechDatabase.get(appContext)
    private val dao = database.offlineDao()
    private val codec = LocalDataStore(appContext)

    suspend fun initialize() {
        database.withTransaction {
            val sql = database.openHelper.writableDatabase
            sql.execSQL("CREATE TABLE IF NOT EXISTS legacy_snapshot_archive AS SELECT * FROM sync_outbox WHERE 0")
            sql.execSQL("INSERT INTO legacy_snapshot_archive SELECT * FROM sync_outbox WHERE entityType = 'device_snapshot'")
            sql.execSQL("DELETE FROM sync_outbox WHERE entityType = 'device_snapshot'")
        }
        val saved = dao.config()
        if (saved != null) {
            val updated = ApiEndpoint.migrate(saved)
            if (updated != saved) dao.saveConfig(updated)
            return
        }
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            .orEmpty().ifBlank { UUID.randomUUID().toString() }
        dao.saveConfig(
            SyncConfigEntity(
                apiBaseUrl = ApiEndpoint.base,
                deviceId = "android-$androidId"
            )
        )
    }

    suspend fun loadSnapshot(): LocalData? = dao.snapshot()?.let { codec.decode(it.payloadJson) }

    fun observeSnapshot(): Flow<LocalData> = dao.observeSnapshot()
        .filterNotNull()
        .map { codec.decode(it.payloadJson) }
        .distinctUntilChanged()

    fun observeSyncConfig(): Flow<SyncConfigEntity> = dao.observeConfig()
        .filterNotNull()
        .distinctUntilChanged()

    suspend fun saveSnapshot(data: LocalData) {
        dao.saveSnapshot(LocalSnapshotEntity(payloadJson = codec.encode(data), updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveAndQueue(
        data: LocalData,
        collection: String,
        entityId: String,
        payload: Map<String, Any?>
    ) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.saveSnapshot(LocalSnapshotEntity(payloadJson = codec.encode(data), updatedAt = now))
            dao.addOperation(
                OutboxEntity(
                    operationId = UUID.randomUUID().toString(),
                    entityType = serverEntityType(collection),
                    entityId = entityId,
                    payloadJson = JSONObject(payload).toString(),
                    createdAt = now
                )
            )
        }
        SyncScheduler.runOnce(appContext)
    }

    suspend fun queueSnapshot(data: LocalData) {
        saveAndQueue(
            data = data,
            collection = "deviceSnapshot",
            entityId = "snapshot",
            payload = mapOf("snapshot" to JSONObject(codec.encode(data)), "capturedAt" to System.currentTimeMillis())
        )
    }

    suspend fun pendingCount(): Int = dao.pendingCount()

    suspend fun configureSession(accessToken: String, refreshToken: String) {
        initialize()
        val current = dao.config() ?: return
        dao.saveConfig(current.copy(
            apiBaseUrl = ApiEndpoint.base,
            accessToken = TokenVault.encrypt(accessToken),
            refreshToken = TokenVault.encrypt(refreshToken),
            lastSyncError = null
        ))
        SyncScheduler.runOnce(appContext)
    }

    /** Clears another shop's cache and queued writes before this device joins a remote shop. */
    suspend fun resetForRemoteShop() {
        initialize()
        database.withTransaction {
            check(dao.pendingCount() == 0) { "Unsent data exists on this device. Export a backup before changing its shop." }
            dao.clearSnapshot()
            dao.clearOutbox()
            val current = dao.config()
            if (current != null) dao.saveConfig(current.copy(pullCursor = 0, lastSyncAt = null, lastSyncError = null))
        }
        codec.save(LocalData())
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) = configureSession(accessToken, refreshToken)

    suspend fun clearTokens(reason: String = "Session expired") {
        initialize()
        val current = dao.config() ?: return
        dao.saveConfig(current.copy(accessToken = "", refreshToken = "", lastSyncError = reason))
    }

    suspend fun deviceId(): String { initialize(); return dao.config()?.deviceId.orEmpty() }

    suspend fun currentTokens(): RemoteTokens? {
        initialize()
        val current = dao.config() ?: return null
        val accessToken = runCatching { TokenVault.decrypt(current.accessToken) }.getOrNull().orEmpty()
        val refreshToken = runCatching { TokenVault.decrypt(current.refreshToken) }.getOrNull().orEmpty()
        return if (accessToken.isBlank() || refreshToken.isBlank()) null else RemoteTokens(accessToken, refreshToken)
    }

    private fun serverEntityType(collection: String): String = when (collection) {
        "products" -> "product"
        "sales" -> "sale"
        "repairs" -> "repair"
        "customers" -> "customer"
        "suppliers" -> "supplier"
        "installments" -> "installment"
        "installmentPayments" -> "installment_payment"
        "ewallets" -> "wallet"
        "expenses" -> "expense"
        "cashClosings" -> "cash_session"
        "supportRequests" -> "support_request"
        "deviceSnapshot" -> "device_snapshot"
        else -> collection.trim().lowercase().replace(Regex("[^a-z0-9_]+"), "_")
    }
}
