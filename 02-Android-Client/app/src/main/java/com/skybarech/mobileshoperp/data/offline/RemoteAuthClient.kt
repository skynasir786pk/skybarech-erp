package com.skybarech.mobileshoperp.data.offline

import com.skybarech.mobileshoperp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteTokens(val accessToken: String, val refreshToken: String)

data class RemoteLoginSession(
    val tokens: RemoteTokens,
    val shopId: String,
    val shopName: String,
    val ownerMobile: String,
    val plan: String,
    val status: String,
    val expiryLabel: String,
    val appearance: JSONObject? = null
)

class RemoteAuthException(
    val statusCode: Int,
    val errorCode: String,
    message: String
) : Exception(message) {
    val isAuthoritativeDenial: Boolean get() = statusCode in setOf(401, 403, 404)
}

object RemoteAuthClient {
    val configured: Boolean get() = BuildConfig.API_BASE_URL.startsWith("https://")

    /**
     * Confirms that the saved shop session still exists on the server.
     * A normal 401 first attempts a token refresh, so an expired access token is
     * not mistaken for a deleted or blocked shop.
     */
    suspend fun validateSession(tokens: RemoteTokens): RemoteTokens? = withContext(Dispatchers.IO) {
        val bootstrap = openConnection("/v1/bootstrap", "GET", tokens.accessToken)
        try {
            val status = bootstrap.responseCode
            if (status in 200..299) return@withContext null
            if (status != 401) throw remoteError(bootstrap, status, "Online verification failed")
        } finally {
            bootstrap.disconnect()
        }

        val refresh = openConnection("/v1/auth/refresh", "POST")
        try {
            refresh.doOutput = true
            refresh.outputStream.bufferedWriter().use {
                it.write(JSONObject().put("refresh_token", tokens.refreshToken).toString())
            }
            val status = refresh.responseCode
            if (status !in 200..299) throw remoteError(refresh, status, "Session refresh failed")
            val json = readJson(refresh, status)
            val refreshed = json.getJSONObject("tokens")
            RemoteTokens(refreshed.getString("access_token"), refreshed.getString("refresh_token"))
        } finally {
            refresh.disconnect()
        }
    }

    suspend fun biometricBootstrap(tokens: RemoteTokens, deviceId: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = openConnection("/v1/biometric/bootstrap", "GET", tokens.accessToken)
        connection.setRequestProperty("X-Device-ID", deviceId)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw remoteError(connection, status, "Device verification failed")
            readJson(connection, status).getJSONObject("shop")
        } finally { connection.disconnect() }
    }

    suspend fun login(identity: String, password: String): RemoteTokens = loginSession(identity, password).tokens

    suspend fun loginSession(identity: String, password: String): RemoteLoginSession = withContext(Dispatchers.IO) {
        val connection = openConnection("/v1/auth/login", "POST")
        val loginJson = try {
            connection.doOutput = true
            connection.outputStream.bufferedWriter().use {
                it.write(JSONObject().put("identity", identity).put("password", password).toString())
            }
            val status = connection.responseCode
            if (status !in 200..299) throw remoteError(connection, status, "Online login failed")
            readJson(connection, status)
        } finally {
            connection.disconnect()
        }

        val tokenJson = loginJson.getJSONObject("tokens")
        val tokens = RemoteTokens(tokenJson.getString("access_token"), tokenJson.getString("refresh_token"))
        val user = loginJson.optJSONObject("user") ?: JSONObject()
        if (user.optString("role") != "super_admin") throw RemoteAuthException(403, "owner_client_required", "This offline app currently requires a shop owner account. Staff cache isolation is not yet available.")
        val bootstrap = openConnection("/v1/bootstrap", "GET", tokens.accessToken)
        try {
            val status = bootstrap.responseCode
            if (status !in 200..299) throw remoteError(bootstrap, status, "Shop bootstrap failed")
            val shop = readJson(bootstrap, status).optJSONObject("shop") ?: JSONObject()
            RemoteLoginSession(
                tokens = tokens,
                shopId = shop.optString("id"),
                shopName = shop.optString("name", "SkyBarech Mobile Shop"),
                ownerMobile = user.optString("mobile", identity),
                plan = shop.optString("plan", "Online"),
                status = shop.optString("status", "active"),
                expiryLabel = shop.optString("expires_at").ifBlank { "Online access verified" },
                appearance = shop.optJSONObject("appearance")
            )
        } finally {
            bootstrap.disconnect()
        }
    }

    suspend fun changePassword(identity: String, current: String, fresh: String): RemoteTokens = withContext(Dispatchers.IO) {
        val session = loginSession(identity, current)
        val connection = openConnection("/v1/auth/password", "POST", session.tokens.accessToken)
        try {
            connection.doOutput = true
            connection.outputStream.bufferedWriter().use {
                it.write(JSONObject().put("current_password", current).put("new_password", fresh).toString())
            }
            val status = connection.responseCode
            if (status !in 200..299) throw remoteError(connection, status, "Password update failed")
            val tokens = readJson(connection, status).getJSONObject("tokens")
            RemoteTokens(tokens.getString("access_token"), tokens.getString("refresh_token"))
        } finally { connection.disconnect() }
    }

    private fun openConnection(path: String, method: String, accessToken: String? = null): HttpURLConnection =
        (URL("${ApiEndpoint.base}$path").openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            useCaches = false
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (!accessToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
        }

    private fun readJson(connection: HttpURLConnection, status: Int): JSONObject {
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        return runCatching { JSONObject(response) }.getOrElse { JSONObject() }
    }

    private fun remoteError(connection: HttpURLConnection, status: Int, fallback: String): RemoteAuthException {
        val apiError = readJson(connection, status).optJSONObject("error")
        return RemoteAuthException(
            statusCode = status,
            errorCode = apiError?.optString("code").orEmpty().ifBlank { "http_$status" },
            message = apiError?.optString("message").orEmpty().ifBlank { "$fallback (HTTP $status)" }
        )
    }
}
