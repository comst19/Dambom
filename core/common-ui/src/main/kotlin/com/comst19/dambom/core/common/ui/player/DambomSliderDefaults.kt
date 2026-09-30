package com.comst19.dambom.core.common.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object DambomSliderDefaults {
    val TrackHeight = 4.dp
    val ThumbDiameter = 16.dp

    @Composable
    fun colors(): SliderColors =
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = ENABLED_TRACK_ALPHA),
            disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_THUMB_ALPHA),
            disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_TRACK_ALPHA),
        )

    @Composable
    fun Thumb(
        enabled: Boolean,
        modifier: Modifier = Modifier,
    ) {
        val colors = colors()
        Spacer(
            modifier.size(ThumbDiameter).background(
                color = if (enabled) colors.thumbColor else colors.disabledThumbColor,
                shape = CircleShape,
            ),
        )
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    fun RangeTrack(
        state: RangeSliderState,
        enabled: Boolean,
        modifier: Modifier = Modifier,
    ) {
        SliderDefaults.Track(
            rangeSliderState = state,
            modifier = modifier.height(TrackHeight),
            colors = colors(),
            enabled = enabled,
            drawStopIndicator = null,
            thumbTrackGapSize = 0.dp,
            trackInsideCornerSize = 0.dp,
        )
    }
}

private const val ENABLED_TRACK_ALPHA = 0.32f
private const val DISABLED_TRACK_ALPHA = 0.12f
private const val DISABLED_THUMB_ALPHA = 0.38f
