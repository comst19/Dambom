package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
internal fun DambomSeekBar(
    value: Float,
    enabled: Boolean,
    contentDescription: String,
    stateDescription: String,
    onValueChange: (Float) -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clampedValue = normalizedSeekBarValue(value)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.32f else 0.12f)
    val thumbColor = if (enabled) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .clipToBounds()
                .semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                    this.stateDescription = stateDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(clampedValue, 0f..1f)
                    if (enabled) {
                        setProgress { target ->
                            onValueChange(normalizedSeekBarValue(target))
                            true
                        }
                    } else {
                        disabled()
                    }
                }.pointerInput(enabled, onValueChange, onInteractionChanged) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        onInteractionChanged(true)
                        try {
                            onValueChange(seekBarValueFromTouch(firstDown.position.x, size.width.toFloat()))
                            do {
                                val event = awaitPointerEvent()
                                event.changes.filter { it.pressed }.forEach { change ->
                                    onValueChange(seekBarValueFromTouch(change.position.x, size.width.toFloat()))
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            onInteractionChanged(false)
                        }
                    }
                },
    ) {
        val centerY = size.height / 2f
        val trackStroke = 4.dp.toPx()
        val thumbRadius = 8.dp.toPx()
        val thumbX = size.width * clampedValue
        drawLine(
            inactiveColor,
            Offset.Zero.copy(y = centerY),
            Offset(size.width, centerY),
            trackStroke,
            StrokeCap.Round,
        )
        drawLine(activeColor, Offset.Zero.copy(y = centerY), Offset(thumbX, centerY), trackStroke, StrokeCap.Round)
        drawCircle(thumbColor, thumbRadius, Offset(thumbX, centerY))
    }
}

internal fun seekBarValueFromTouch(
    x: Float,
    width: Float,
): Float =
    if (x.isFinite() && width.isFinite() && width > 0f) {
        normalizedSeekBarValue(x / width)
    } else {
        0f
    }

internal fun normalizedSeekBarValue(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

internal fun isDambomSeekBarEnabled(durationMillis: Long): Boolean = durationMillis > 0L
