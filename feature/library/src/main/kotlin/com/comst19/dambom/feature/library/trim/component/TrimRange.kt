package com.comst19.dambom.feature.library.trim.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.toTimeText
import com.comst19.dambom.feature.library.trim.contract.TrimSelection
import com.comst19.dambom.feature.library.trim.contract.VideoTrimState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TrimRange(
    state: VideoTrimState,
    onSelect: (TrimSelection) -> Unit,
    positionMillis: Long,
) {
    var range by remember(state.selection) {
        mutableStateOf(state.selection.startMillis.toFloat()..state.selection.endMillis.toFloat())
    }
    val label = stringResource(R.string.trim_range)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.trim_choose_scene),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    R.string.trim_duration,
                    (range.endInclusive - range.start) / MILLIS_PER_SECOND,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RangeSlider(
            value = range,
            onValueChange = { next -> if (next.endInclusive - next.start >= 1f) range = next },
            onValueChangeFinished = { onSelect(TrimSelection(range.start.toLong(), range.endInclusive.toLong())) },
            valueRange = 0f..state.durationMillis.toFloat(),
            enabled = !state.exporting,
            startThumb = { TrimHandle() },
            endThumb = { TrimHandle() },
            track = {
                TrimFilmstrip(checkNotNull(state.task?.localFilePath), state.durationMillis, range, positionMillis)
            },
            modifier = Modifier.fillMaxWidth().systemGestureExclusion().semantics { contentDescription = label },
        )
        TrimRuler(state.durationMillis)
        Text(
            stringResource(R.string.trim_range_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrimHandle() {
    Box(
        Modifier
            .size(width = 16.dp, height = 28.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 2.dp, height = 12.dp)
                .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(1.dp)),
        )
    }
}

private const val MILLIS_PER_SECOND = 1_000f

@Composable
private fun TrimRuler(durationMillis: Long) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            for (tick in 0..RULER_TICKS) {
                val x = size.width * tick / RULER_TICKS
                val major = tick % TICKS_PER_LABEL == 0
                drawLine(
                    color.copy(alpha = if (major) 0.8f else 0.35f),
                    Offset(x, 0f),
                    Offset(x, if (major) size.height else size.height / 2f),
                    1.dp.toPx(),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (index in 0..RULER_LABEL_INTERVALS) {
                Text(
                    (durationMillis * index / RULER_LABEL_INTERVALS).toTimeText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
            }
        }
    }
}

private const val RULER_TICKS = 20
private const val TICKS_PER_LABEL = 5
private const val RULER_LABEL_INTERVALS = 4
