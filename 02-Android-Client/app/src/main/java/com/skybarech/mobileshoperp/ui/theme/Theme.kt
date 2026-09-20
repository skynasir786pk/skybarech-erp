package com.skybarech.mobileshoperp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

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
    val dark = UiAppearance.dark
    val canvas = AppCanvas.toArgb()
    androidx.compose.runtime.SideEffect {
        activity?.let {
            if (android.os.Build.VERSION.SDK_INT < 35) {
                @Suppress("DEPRECATION")
                it.window.statusBarColor = canvas
                @Suppress("DEPRECATION")
                it.window.navigationBarColor = canvas
            }
            androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = skyBarechScheme(),
        typography = SkyBarechTypography,
        content = content
    )
}
