package com.comst19.dambom.core.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowSizeClass

enum class WindowWidthSizeClass {
    Compact,
    Medium,
    Expanded,
}

enum class WindowHeightSizeClass {
    Compact,
    Medium,
    Expanded,
}

@Immutable
data class AdaptiveLayoutInfo(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
) {
    val isCompactHeight: Boolean
        get() = heightSizeClass == WindowHeightSizeClass.Compact

    val supportsMultiplePanes: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.Expanded && !isCompactHeight
}

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun currentAdaptiveLayoutInfo(): AdaptiveLayoutInfo = currentWindowAdaptiveInfoV2().windowSizeClass.toAdaptiveLayoutInfo()

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(AdaptiveLayoutInfo) -> Unit,
) {
    Box(modifier) {
        val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
        Box { content(windowSizeClass.toAdaptiveLayoutInfo()) }
    }
}

fun WindowSizeClass.toAdaptiveLayoutInfo(): AdaptiveLayoutInfo =
    AdaptiveLayoutInfo(
        widthSizeClass = toLocalWidthSizeClass(),
        heightSizeClass = toLocalHeightSizeClass(),
    )

private fun WindowSizeClass.toLocalWidthSizeClass(): WindowWidthSizeClass =
    when {
        isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> WindowWidthSizeClass.Expanded
        isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Compact
    }

private fun WindowSizeClass.toLocalHeightSizeClass(): WindowHeightSizeClass =
    when {
        isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND) -> WindowHeightSizeClass.Expanded
        isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) -> WindowHeightSizeClass.Medium
        else -> WindowHeightSizeClass.Compact
    }
