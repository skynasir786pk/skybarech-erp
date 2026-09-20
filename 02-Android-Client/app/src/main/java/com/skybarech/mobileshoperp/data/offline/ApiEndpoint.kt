package com.skybarech.mobileshoperp.data.offline

import com.skybarech.mobileshoperp.BuildConfig
import java.net.URI
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ApiEndpoint {
    val base: String get() = validate(BuildConfig.API_BASE_URL)

    fun validate(value: String): String {
        val uri = URI(value.trim().trimEnd('/'))
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && !value.contains("YOUR-DOMAIN", true)) {
            "The app server address is invalid. Install the build configured by your administrator."
        }
        return uri.toASCIIString()
    }

    fun migrate(current: SyncConfigEntity, target: String = base): SyncConfigEntity {
        val next = validate(target)
        if (current.apiBaseUrl == next) return current
        val sameOrigin = runCatching {
            val old = URI(current.apiBaseUrl)
            val fresh = URI(next)
            old.scheme == fresh.scheme && old.host == fresh.host && old.port == fresh.port
        }.getOrDefault(false)
        // Never forward an old server's credentials to a different host.
        // Keep all records, queued operations and cursor during a URL update.
        return current.copy(apiBaseUrl = next,
            accessToken = if (sameOrigin) current.accessToken else "",
            refreshToken = if (sameOrigin) current.refreshToken else "",
            lastSyncError = if (sameOrigin) null else "SERVER_CHANGED|Server updated. Sign in again to reconnect your shop.")
    }

    fun message(error: Throwable): String = when {
        error is UnknownHostException -> "DNS_UNAVAILABLE|Cannot find the server. Check Wi-Fi/mobile data or Private DNS, then retry."
        error is SocketTimeoutException -> "TIMEOUT|The server took too long. Your saved changes will retry."
        error is SSLException -> "TLS_ERROR|Secure connection failed. Check phone date/time and the server certificate."
        else -> error.message?.take(300) ?: "Connection interrupted. Your saved changes will retry."
    }

    suspend fun checkConnection(): String = withContext(Dispatchers.IO) {
        val connection = URL("$base/health").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) return@withContext "Server replied HTTP $status. Ask your administrator to check the API deployment."
            val health = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (health.optBoolean("success") && health.optString("database") == "connected")
                "Connected. Server and database are available."
            else "The server responded, but the database is not ready."
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            message(error).substringAfter('|')
        }
        finally { connection.disconnect() }
    }
}
