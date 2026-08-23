package com.comst19.dambom.core.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

@Composable
fun StatusBarProtection(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
) {
    val density = LocalDensity.current
    val height = with(density) { (WindowInsets.statusBars.getTop(this) * HEIGHT_MULTIPLIER).toDp() }

    Spacer(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            color,
                            color.copy(alpha = PROTECTION_ALPHA),
                            Color.Transparent,
                        ),
                    ),
                ),
    )
}

private const val HEIGHT_MULTIPLIER = 1.2f
private const val PROTECTION_ALPHA = 0.8f
