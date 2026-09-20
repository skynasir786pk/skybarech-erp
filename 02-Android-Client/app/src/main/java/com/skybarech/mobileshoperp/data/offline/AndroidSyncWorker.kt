package com.skybarech.mobileshoperp.data.offline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.min

private class SyncHttpException(val status: Int, val responseBody: String) : Exception("HTTP $status: ${responseBody.take(300)}") {
    val errorCode: String = runCatching { JSONObject(responseBody).optJSONObject("error")?.optString("code").orEmpty() }.getOrDefault("")
    val serverMessage: String = runCatching { JSONObject(responseBody).optJSONObject("error")?.optString("message").orEmpty() }.getOrDefault("")
}

class AndroidSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object { private val syncLock = Mutex() }
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) { syncLock.withLock { doSyncWork() } }

    private suspend fun doSyncWork(): Result {
        OfflineRepository(applicationContext).initialize()
        val dao = SkyBarechDatabase.get(applicationContext).offlineDao()
        var config = dao.config() ?: return Result.success()
        if (config.apiBaseUrl.isBlank() || config.accessToken.isBlank()) return Result.success()

        var accessToken = runCatching { TokenVault.decrypt(config.accessToken) }.getOrElse {
            dao.syncFailed("Secure sync token could not be read")
            return Result.failure()
        }

        return try {
            executeSync(dao, config, accessToken)
            Result.success()
        } catch (error: SyncHttpException) {
            if (error.status == 401 && config.refreshToken.isNotBlank()) {
                try {
                    config = refreshSession(dao, config)
                    accessToken = TokenVault.decrypt(config.accessToken)
                    executeSync(dao, config, accessToken)
                    Result.success()
                } catch (refreshError: Exception) {
                    if (refreshError is CancellationException) throw refreshError
                    val message = ApiEndpoint.message(refreshError)
                    if (refreshError is SyncHttpException && refreshError.status in setOf(401, 403)) {
                        val reason = refreshError.serverMessage.ifBlank { "Online session expired or access was revoked" }
                        dao.saveConfig(config.copy(accessToken = "", refreshToken = "", lastSyncError = "ACCESS_REVOKED|$reason"))
                        markReadyFailed(dao, reason)
                        Result.failure()
                    } else {
                        markReadyFailed(dao, message)
                        dao.syncFailed(message)
                        Result.retry()
                    }
                }
            } else {
                val serverMessage = error.serverMessage.ifBlank { error.message?.take(500) ?: "Sync request failed" }
                val hardShopAccessError = error.errorCode in setOf("shop_inactive", "shop_expired", "user_inactive", "session_invalid")
                val blockedDevice = error.errorCode in setOf("device_blocked", "device_required")
                when {
                    hardShopAccessError -> {
                        dao.saveConfig(config.copy(accessToken = "", refreshToken = "", lastSyncError = "ACCESS_REVOKED|$serverMessage"))
                        markReadyFailed(dao, serverMessage)
                        Result.failure()
                    }
                    blockedDevice -> {
                        dao.saveConfig(config.copy(accessToken = "", refreshToken = "", lastSyncError = "DEVICE_BLOCKED|$serverMessage"))
                        markReadyFailed(dao, serverMessage)
                        Result.failure()
                    }
                    else -> {
                        markReadyFailed(dao, serverMessage)
                        dao.syncFailed(serverMessage)
                        if (error.status in setOf(401, 403, 404)) Result.failure() else Result.retry()
                    }
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val message = ApiEndpoint.message(error)
            markReadyFailed(dao, message)
            dao.syncFailed(message)
            Result.retry()
        }
    }

    private suspend fun executeSync(dao: OfflineDao, config: SyncConfigEntity, accessToken: String) {
        val bootstrap = JSONObject(getJson("${config.apiBaseUrl.trimEnd('/')}/v1/bootstrap", accessToken, config.deviceId))
        val shop = bootstrap.optJSONObject("shop")
        if (shop != null) {
            val store = com.skybarech.mobileshoperp.data.local.ShopSessionStore(applicationContext)
            val session = store.read()
            check(session.shopId == shop.optString("id")) { "Server shop does not match this installation. Sign in again." }
            com.skybarech.mobileshoperp.ui.theme.UiAppearance.saveRemote(applicationContext, shop.optJSONObject("appearance"))
            store.save(session.copy(
                shopName = shop.optString("name", session.shopName), plan = shop.optString("plan", session.plan),
                status = shop.optString("status", session.status), expiryLabel = shop.optString("expires_at").takeIf { it.isNotBlank() && it != "null" } ?: "No expiry set",
                ownerMobile = shop.optString("owner_mobile", session.ownerMobile)))
            applicationContext.getSharedPreferences("skybarech_shop_profile", Context.MODE_PRIVATE).edit()
                .putString("address", listOf(shop.optString("address"), shop.optString("city")).filter { it.isNotBlank() }.joinToString(", ")).apply()
        }
        val operations = dao.readyOperations(System.currentTimeMillis(), 100)
        if (operations.isNotEmpty()) dao.markSyncing(operations.map { it.operationId })

        postJson(
            "${config.apiBaseUrl.trimEnd('/')}/v1/devices/register",
            accessToken,
            config.deviceId,
            JSONObject().put("device_id", config.deviceId).put("platform", "android").toString()
        )

        if (operations.isNotEmpty()) {
            val body = JSONObject().put("operations", JSONArray(operations.map { operation ->
                JSONObject()
                    .put("operation_id", operation.operationId)
                    .put("entity_type", operation.entityType)
                    .put("entity_id", operation.entityId)
                    .put("action", operation.actionName)
                    .put("payload", JSONObject(operation.payloadJson))
                    .put("device_id", config.deviceId)
            }))
            val response = postJson(
                "${config.apiBaseUrl.trimEnd('/')}/v1/sync/push",
                accessToken,
                config.deviceId,
                body.toString()
            )
            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: JSONArray()
            val acknowledged = mutableSetOf<String>()
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val operationId = item.optString("operation_id")
                val status = item.optString("status")
                if (operationId.isBlank()) continue
                if (status in setOf("applied", "duplicate", "synced")) {
                    dao.markSynced(listOf(operationId), System.currentTimeMillis())
                    acknowledged += operationId
                } else {
                    dao.markFailed(operationId, item.optString("error", status.ifBlank { "Server rejected operation" }), nextRetry(1))
                    acknowledged += operationId
                }
            }
            operations.filterNot { it.operationId in acknowledged }.forEach {
                dao.markFailed(it.operationId, "Server did not acknowledge operation", nextRetry(it.attempts + 1))
            }
        }

        var cursor = (dao.config() ?: config).pullCursor
        do {
            val pulled = JSONObject(getJson("${config.apiBaseUrl.trimEnd('/')}/v1/sync/pull?cursor=$cursor&limit=500", accessToken, config.deviceId))
            check(pulled.optBoolean("success") && pulled.has("records") && pulled.has("next_cursor")) { "Invalid server data response" }
            val records = pulled.getJSONArray("records")
            val next = pulled.getLong("next_cursor")
            check(next >= cursor && (records.length() == 0 || next > cursor)) { "Server sync cursor did not advance" }
            SkyBarechDatabase.get(applicationContext).withTransaction {
                var merged = RemoteMerge.merge(dao.snapshot()?.payloadJson ?: "{}", records)
                // A save made while the HTTP request was running wins locally until uploaded.
                val pending = JSONArray(dao.unsentOperations().filter { it.entityType != "device_snapshot" }.map {
                    JSONObject().put("entity_type", it.entityType).put("entity_id", it.entityId)
                        .put("payload", JSONObject(it.payloadJson)).put("is_deleted", it.actionName == "delete")
                })
                merged = RemoteMerge.merge(merged, pending)
                if (records.length() > 0) dao.saveSnapshot(LocalSnapshotEntity(payloadJson = merged, updatedAt = System.currentTimeMillis()))
                dao.pullSucceeded(next, System.currentTimeMillis())
            }
            cursor = next
        } while (records.length() == 500)
        if (dao.pendingCount() > 0) dao.syncFailed("PENDING|Some changes are waiting to upload. Open sync details before closing this device.")
    }

    private suspend fun refreshSession(dao: OfflineDao, config: SyncConfigEntity): SyncConfigEntity {
        val refreshToken = runCatching { TokenVault.decrypt(config.refreshToken) }.getOrElse { throw IllegalStateException("Secure refresh token could not be read") }
        if (refreshToken.isBlank()) throw IllegalStateException("Refresh token is missing")
        val response = postPublicJson(
            "${config.apiBaseUrl.trimEnd('/')}/v1/auth/refresh",
            JSONObject().put("refresh_token", refreshToken).toString()
        )
        val tokens = JSONObject(response).optJSONObject("tokens") ?: throw IllegalStateException("Server returned invalid refresh credentials")
        val access = tokens.optString("access_token")
        val refresh = tokens.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) throw IllegalStateException("Server returned incomplete refresh credentials")
        val updated = config.copy(
            accessToken = TokenVault.encrypt(access),
            refreshToken = TokenVault.encrypt(refresh),
            lastSyncError = null
        )
        dao.saveConfig(updated)
        return updated
    }

    private suspend fun markReadyFailed(dao: OfflineDao, message: String) {
        dao.readyOperations(System.currentTimeMillis(), 100).forEach {
            dao.markFailed(it.operationId, message, nextRetry(it.attempts + 1))
        }
    }

    private fun getJson(url: String, token: String, deviceId: String): String {
        require(url.startsWith("https://")) { "Sync API must use HTTPS" }
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("X-Device-ID", deviceId)
            connection.setRequestProperty("X-Client-Version", "android-1.3.16")
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw SyncHttpException(status, response)
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun postJson(url: String, token: String, deviceId: String, body: String): String {
        require(url.startsWith("https://")) { "Sync API must use HTTPS" }
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("X-Device-ID", deviceId)
            connection.setRequestProperty("X-Client-Version", "android-1.3.16")
            connection.outputStream.bufferedWriter().use { it.write(body) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw SyncHttpException(status, response)
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun postPublicJson(url: String, body: String): String {
        require(url.startsWith("https://")) { "Sync API must use HTTPS" }
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Client-Version", "android-1.3.16")
            connection.outputStream.bufferedWriter().use { it.write(body) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw SyncHttpException(status, response)
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun nextRetry(attempt: Int): Long {
        val delaySeconds = min(3600L, 15L * (1L shl min(attempt, 8)))
        return System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delaySeconds)
    }
}

object SyncScheduler {
    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AndroidSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "skybarech-periodic-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runOnce(context: Context) {
        val request = OneTimeWorkRequestBuilder<AndroidSyncWorker>()
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "skybarech-immediate-sync",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
