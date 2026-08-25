package com.comst19.dambom.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun AdaptiveTwoColumnLayout(
    header: @Composable () -> Unit,
    primary: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = currentWindowAdaptiveInfoV2().windowSizeClass.supportsMultiplePanes

    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        header()
        Spacer(Modifier.height(28.dp))
        if (expanded) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) { primary() }
                Column(Modifier.weight(1f)) { supporting() }
            }
        } else {
            primary()
            Spacer(Modifier.height(32.dp))
            supporting()
        }
    }
}

private val WindowSizeClass.supportsMultiplePanes: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
