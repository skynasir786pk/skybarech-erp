package com.skybarech.mobileshoperp

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import android.graphics.Color
import com.skybarech.mobileshoperp.ui.SkyBarechApp
import com.skybarech.mobileshoperp.ui.theme.SkyBarechTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        com.skybarech.mobileshoperp.ui.theme.UiAppearance.initialize(applicationContext)
        setContent {
            SkyBarechTheme {
                SkyBarechApp()
            }
        }
    }
}
