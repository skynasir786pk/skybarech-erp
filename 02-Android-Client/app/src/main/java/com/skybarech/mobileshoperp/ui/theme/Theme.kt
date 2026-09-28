package com.skybarech.mobileshoperp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance

private fun skyBarechScheme() = (if (UiAppearance.dark) darkColorScheme() else lightColorScheme()).copy(
    primary = BrandBlue,
    onPrimary = contrastingInk(BrandBlue),
    primaryContainer = BrandBlueSoft,
    onPrimaryContainer = BrandBlueDark,
    secondary = BrandBlueDark,
    onSecondary = contrastingInk(BrandBlueDark),
    background = AppCanvas,
    onBackground = Ink,
    surface = CardSurface,
    onSurface = Ink,
    surfaceVariant = BrandBlueSoft,
    onSurfaceVariant = MutedInk,
    outline = CardStroke,
    error = Danger
)

@Composable
fun SkyBarechTheme(content: @Composable () -> Unit) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    androidx.compose.runtime.SideEffect {
        activity?.let {
            @Suppress("DEPRECATION")
            it.window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            it.window.navigationBarColor = android.graphics.Color.TRANSPARENT
            androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    MaterialTheme(
        colorScheme = skyBarechScheme(),
        typography = SkyBarechTypography,
        content = content
    )
}
