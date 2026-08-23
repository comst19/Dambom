package com.comst19.dambom.core.common.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection

@Composable
fun AppScreen(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val appPadding = LocalAppScaffoldPadding.current
    val layoutDirection = LocalLayoutDirection.current
    val outerPadding =
        PaddingValues(
            start = appPadding.calculateStartPadding(layoutDirection),
            end = appPadding.calculateEndPadding(layoutDirection),
            bottom = appPadding.calculateBottomPadding(),
        )

    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .padding(outerPadding)
                .consumeWindowInsets(outerPadding),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = topBar,
        content = content,
    )
}
