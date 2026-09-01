package com.comst19.dambom.presentation.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.navigation3.runtime.NavKey

@Immutable
internal data class AppChrome(
    val showNavigation: Boolean,
    val useDarkStatusBarIcons: Boolean,
    val useDarkNavigationBarIcons: Boolean,
)

@Composable
internal fun appChrome(
    currentKey: NavKey,
    usesNavigationRail: Boolean,
): AppChrome {
    val showNavigation = currentKey in AppNavigationConfig.bottomBarKeys
    val statusBarBackground = MaterialTheme.colorScheme.surface
    val navigationBarBackground =
        if (showNavigation && !usesNavigationRail) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    return AppChrome(
        showNavigation = showNavigation,
        useDarkStatusBarIcons = statusBarBackground.isLightBackground(),
        useDarkNavigationBarIcons = navigationBarBackground.isLightBackground(),
    )
}

private fun Color.isLightBackground(): Boolean = luminance() > LIGHT_THRESHOLD

private const val LIGHT_THRESHOLD = 0.5f
