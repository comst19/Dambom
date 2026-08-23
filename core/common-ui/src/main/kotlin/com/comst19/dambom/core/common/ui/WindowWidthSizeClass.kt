package com.comst19.dambom.core.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class WindowWidthSizeClass {
    Compact,
    Medium,
    Expanded,
}

@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(WindowWidthSizeClass) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val widthSizeClass =
            when {
                maxWidth < COMPACT_MAX_WIDTH -> WindowWidthSizeClass.Compact
                maxWidth < MEDIUM_MAX_WIDTH -> WindowWidthSizeClass.Medium
                else -> WindowWidthSizeClass.Expanded
            }
        Box { content(widthSizeClass) }
    }
}

private val COMPACT_MAX_WIDTH = 600.dp
private val MEDIUM_MAX_WIDTH = 840.dp
