package com.comst19.dambom.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.designsystem.sampleDetailBackground
import com.comst19.dambom.core.navigation.contract.HomeGraph.SampleDetailKey
import kotlin.reflect.KClass

internal object AppUiPolicy {
    val fullScreenRoutes: Set<KClass<out NavKey>> = setOf(SampleDetailKey::class)
}

@Immutable
internal data class AppChrome(
    val showBottomBar: Boolean,
    val useDarkStatusBarIcons: Boolean,
    val useDarkNavigationBarIcons: Boolean,
)

@Composable
internal fun appChrome(currentKey: NavKey): AppChrome {
    val isFullScreen = currentKey.isIn(AppUiPolicy.fullScreenRoutes)
    val showBottomBar = currentKey in AppNavigationConfig.bottomBarKeys
    val statusBarBackground =
        if (isFullScreen) MaterialTheme.colorScheme.sampleDetailBackground else MaterialTheme.colorScheme.surface
    val navigationBarBackground =
        when {
            isFullScreen -> MaterialTheme.colorScheme.sampleDetailBackground
            showBottomBar -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surface
        }

    return AppChrome(
        showBottomBar = showBottomBar,
        useDarkStatusBarIcons = statusBarBackground.isLightBackground(),
        useDarkNavigationBarIcons = navigationBarBackground.isLightBackground(),
    )
}

private fun NavKey.isIn(routes: Set<KClass<out NavKey>>): Boolean = routes.any { it.isInstance(this) }

private fun Color.isLightBackground(): Boolean = luminance() > LIGHT_THRESHOLD

private const val LIGHT_THRESHOLD = 0.5f
