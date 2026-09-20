package com.skybarech.mobileshoperp.ui.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** Presentation-only preference: never changes saved entity values or Cloud requests. */
object UiLanguage {
    var code by mutableStateOf("en")
        private set
    private var loaded = false
    private var catalog = JSONObject()
    private var aliases = emptyMap<String, String>()
    private var patterns = emptyList<Pair<String, Regex>>()
    // Android uses ICU: BOTH literal braces must be escaped (unlike desktop Java).
    private val placeholder = Regex("\\{(\\d+)\\}")
    private fun normalize(text: String) = text.replace(Regex("\\s+"), " ").trim()
    fun initialize(context: Context) {
        if (loaded) return
        catalog = JSONObject(context.assets.open("translations.json").bufferedReader().use { it.readText() })
        val keys = catalog.keys().asSequence().toList()
        aliases = keys.associateBy { it.lowercase(java.util.Locale.ROOT) }
        patterns = keys.filter { it.contains(placeholder) && it.replace(placeholder, "").isNotBlank() }
            .sortedByDescending { it.replace(placeholder, "").length }
            .map { key ->
                val regex = StringBuilder("^")
                var position = 0
                placeholder.findAll(key).forEach { match ->
                    regex.append(Regex.escape(key.substring(position, match.range.first))).append("(.*?)")
                    position = match.range.last + 1
                }
                regex.append(Regex.escape(key.substring(position))).append("$")
                key to Regex(regex.toString(), RegexOption.IGNORE_CASE)
            }
        code = context.getSharedPreferences("skybarech_ui", Context.MODE_PRIVATE).getString("language", "en").let { if (it == "ur") "ur" else "en" }
        loaded = true
    }
    fun set(context: Context, value: String) {
        code = if (value == "ur") "ur" else "en"
        context.getSharedPreferences("skybarech_ui", Context.MODE_PRIVATE).edit().putString("language", code).apply()
    }
    fun text(value: String): String {
        val key = normalize(value)
        val known = if (catalog.has(key)) key else aliases[key.lowercase(java.util.Locale.ROOT)]
        if (known != null) return catalog.getJSONObject(known).optString(code, known)
        // Required markers are presentation only, never part of a submitted field value.
        if (key.endsWith(" *")) return text(key.removeSuffix(" *")) + " *"
        for ((pattern, regex) in patterns) {
            val match = regex.matchEntire(key) ?: continue
            val indexes = placeholder.findAll(pattern).map { it.groupValues[1] }.toList()
            val args = indexes.mapIndexed { i, index -> index to match.groupValues[i + 1] }.toMap()
            return placeholder.replace(catalog.getJSONObject(pattern).optString(code, pattern)) { args[it.groupValues[1]] ?: it.value }
        }
        return value
    }
}
fun tr(value: String): String = UiLanguage.text(value)
