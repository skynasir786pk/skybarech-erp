package com.skybarech.mobileshoperp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

object UiAppearance {
    private var localDark by mutableStateOf(false)
    private var palette by mutableStateOf<Map<String, Color>?>(null)
    private var initialized = false
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ -> read(prefs) }
    var authLight by mutableStateOf(false)
    var darkBrandBackdrop by mutableStateOf(false)
    var dark: Boolean
        get() = !authLight && localDark
        set(value) { localDark = value }
    val managed: Boolean get() = !authLight && palette != null
    // A remotely supplied dark palette must not silently switch a light workspace.
    fun color(key: String): Color? = if (authLight) null else palette?.get(key)?.takeIf {
        when (key) {
            "surface", "background" -> if (dark) it.luminance() < .179f else it.luminance() > .8f
            "accent" -> if (dark) it.luminance() > .35f else it.luminance() < .35f
            else -> true
        }
    }
    fun initialize(context: android.content.Context) {
        if (initialized) return
        initialized = true
        val prefs = context.applicationContext.getSharedPreferences("skybarech_appearance", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("light_design_1326", false)) {
            prefs.edit().putBoolean("dark", false).putBoolean("light_design_1326", true).apply()
        }
        read(prefs)
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }
    private fun read(prefs: android.content.SharedPreferences) {
        localDark = prefs.getBoolean("dark", false)
        palette = runCatching {
            val json = org.json.JSONObject(prefs.getString("remote", "{}").orEmpty())
            if (!json.optBoolean("enabled")) null
            else listOf("accent", "background", "surface").associateWith { key ->
                val hex = json.getString(key)
                require(Regex("#[0-9a-fA-F]{6}").matches(hex))
                Color(android.graphics.Color.parseColor(hex))
            }
        }.getOrNull()
    }
    fun saveRemote(context: android.content.Context, settings: org.json.JSONObject?) {
        val androidPalette = settings?.optJSONObject("android") ?: org.json.JSONObject()
        context.applicationContext.getSharedPreferences("skybarech_appearance", android.content.Context.MODE_PRIVATE)
            .edit().putString("remote", androidPalette.toString()).apply()
    }
}
fun contrastingInk(color: Color): Color = if (color.luminance() > .179f) Color.Black else Color.White
val CardSurface: Color get() = UiAppearance.color("surface") ?: if (UiAppearance.dark) Color(0xFF0B2344) else Color.White
val BrandBlue: Color get() = UiAppearance.color("accent") ?: if (UiAppearance.dark) Color(0xFF9DBEFF) else Color(0xFF0F43D8)
val BrandBlueDark: Color get() = if (UiAppearance.managed) BrandBlue else if (UiAppearance.dark) Color(0xFFBDCEFF) else Color(0xFF062D9B)
val BrandBlueSoft: Color get() = if (UiAppearance.managed) androidx.compose.ui.graphics.lerp(CardSurface, BrandBlue, .10f) else if (UiAppearance.dark) Color(0xFF233B62) else Color(0xFFEAF0FF)
val Ink: Color get() = if (UiAppearance.managed) contrastingInk(CardSurface) else if (UiAppearance.dark) Color(0xFFF0F5FF) else Color(0xFF0F1D3F)
val MutedInk: Color get() = if (UiAppearance.managed) Ink else if (UiAppearance.dark) Color(0xFFBBC8DC) else Color(0xFF64708A)
val AppCanvas: Color get() = UiAppearance.color("background") ?: if (UiAppearance.dark) Color(0xFF000E24) else Color(0xFFF7F9FE)
val CardStroke: Color get() = if (UiAppearance.managed) androidx.compose.ui.graphics.lerp(CardSurface, Ink, .20f) else if (UiAppearance.dark) Color(0xFF40516A) else Color(0xFFE3E9F7)
val Success = Color(0xFF087A48)
val SuccessSoft = Color(0xFFE8F8EF)
val Danger = Color(0xFFB52B39)
val DangerSoft = Color(0xFFFFECEE)
val Warning = Color(0xFF975800)
val WarningSoft = Color(0xFFFFF4E4)
val Purple = Color(0xFF7C63E8)
val PurpleSoft = Color(0xFFF1EDFF)
