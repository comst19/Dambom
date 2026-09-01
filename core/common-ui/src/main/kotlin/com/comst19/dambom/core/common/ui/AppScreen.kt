package com.comst19.dambom.core.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppScreen(
    modifier: Modifier = Modifier,
    maxWidth: Dp? = null,
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val appPadding = LocalAppScaffoldPadding.current
    val layoutDirection = LocalLayoutDirection.current
    val additionalTopPadding =
        (
            appPadding.calculateTopPadding() -
                WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        ).coerceAtLeast(0.dp)
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
                .consumeWindowInsets(outerPadding)
                .padding(top = additionalTopPadding),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            if (maxWidth == null) {
                topBar()
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.widthIn(max = maxWidth).fillMaxWidth()) {
                        topBar()
                    }
                }
            }
        },
        content = { innerPadding ->
            if (maxWidth == null) {
                content(innerPadding)
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier
                            .widthIn(max = maxWidth)
                            .fillMaxWidth()
                            .fillMaxHeight(),
                    ) {
                        content(innerPadding)
                    }
                }
            }
        },
    )
}

object AppScreenDefaults {
    val SinglePaneMaxWidth = 720.dp
}
