package com.skybarech.mobileshoperp

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import com.skybarech.mobileshoperp.ui.SkyBarechApp
import com.skybarech.mobileshoperp.ui.theme.SkyBarechTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.skybarech.mobileshoperp.ui.theme.UiAppearance.initialize(applicationContext)
        setContent {
            SkyBarechTheme {
                SkyBarechApp()
            }
        }
    }
}
