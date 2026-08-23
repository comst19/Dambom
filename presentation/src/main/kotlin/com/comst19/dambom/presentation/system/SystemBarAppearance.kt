package com.comst19.dambom.presentation.system

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.comst19.dambom.presentation.AppChrome

@Composable
internal fun SystemBarAppearance(chrome: AppChrome) {
    val view = LocalView.current
    SideEffect {
        if (!view.isInEditMode) {
            val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = chrome.useDarkStatusBarIcons
                isAppearanceLightNavigationBars = chrome.useDarkNavigationBarIcons
            }
        }
    }
}
