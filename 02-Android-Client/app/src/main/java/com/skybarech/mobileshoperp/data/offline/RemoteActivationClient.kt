package com.skybarech.mobileshoperp.data.offline

import com.skybarech.mobileshoperp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class VerifiedActivation(
    val activationId: Int,
    val shopId: String,
    val shopName: String,
    val code: String,
    val mobile: String,
    val temporaryPassword: String
)

object RemoteActivationClient {
    suspend fun verify(code: String, mobile: String, temporaryPassword: String): VerifiedActivation {
        val response = request("/v1/activation/verify", JSONObject()
            .put("code", code).put("mobile", mobile).put("temporary_password", temporaryPassword))
        val activation = response.getJSONObject("activation")
        return VerifiedActivation(
            activationId = activation.getInt("activation_id"),
            shopId = activation.get("shop_id").toString(),
            shopName = activation.getString("shop_name"),
            code = code,
            mobile = mobile,
            temporaryPassword = temporaryPassword
        )
    }

    suspend fun complete(activation: VerifiedActivation, newPassword: String): RemoteTokens {
        val response = request("/v1/activation/complete", JSONObject()
            .put("activation_id", activation.activationId)
            .put("code", activation.code)
            .put("mobile", activation.mobile)
            .put("temporary_password", activation.temporaryPassword)
            .put("new_password", newPassword)
            .put("full_name", "Shop Owner"))
        val tokens = response.getJSONObject("tokens")
        return RemoteTokens(tokens.getString("access_token"), tokens.getString("refresh_token"))
    }

    private suspend fun request(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        require(BuildConfig.API_BASE_URL.startsWith("https://")) { "HTTPS API is not configured" }
        val connection = URL("${ApiEndpoint.base}$path").openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val status = connection.responseCode
            val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (status !in 200..299) error(json.optJSONObject("error")?.optString("message") ?: "Activation failed (HTTP $status)")
            json
        } finally {
            connection.disconnect()
        }
    }
}
