package com.umain.draugr.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

@Composable
actual fun applySystemBarStyle() {
    val activity = LocalContext.current as? Activity ?: return
    SideEffect {
        val window = activity.window
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.statusBarColor = Background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Background.toArgb()
        }
    }
}
