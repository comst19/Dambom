package com.comst19.dambom.feature.library.trim.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.ui.player.DambomSliderDefaults
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.toTimeText
import com.comst19.dambom.feature.library.trim.contract.TrimSelection
import com.comst19.dambom.feature.library.trim.contract.VideoTrimState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TrimRange(
    state: VideoTrimState,
    onSelect: (TrimSelection) -> Unit,
) {
    var range by remember(state.selection) {
        mutableStateOf(state.selection.startMillis.toFloat()..state.selection.endMillis.toFloat())
    }
    val label = stringResource(R.string.trim_range)
    Text(
        stringResource(
            R.string.trim_start_end,
            range.start.toLong().toTrimTimeText(),
            range.endInclusive.toLong().toTrimTimeText(),
        ),
    )
    RangeSlider(
        value = range,
        onValueChange = { next -> if (next.endInclusive - next.start >= 1f) range = next },
        onValueChangeFinished = { onSelect(TrimSelection(range.start.toLong(), range.endInclusive.toLong())) },
        valueRange = 0f..state.durationMillis.toFloat(),
        enabled = !state.exporting,
        startThumb = { DambomSliderDefaults.Thumb(enabled = !state.exporting) },
        endThumb = { DambomSliderDefaults.Thumb(enabled = !state.exporting) },
        track = { DambomSliderDefaults.RangeTrack(it, enabled = !state.exporting) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).semantics { contentDescription = label },
    )
    Text(stringResource(R.string.trim_duration, (range.endInclusive - range.start).toLong().toTrimTimeText()))
}

private fun Long.toTrimTimeText(): String {
    val millis = (coerceAtLeast(0L) % MILLIS_PER_SECOND).toString().padStart(MILLIS_DIGITS, '0')
    return "${toTimeText()}.$millis"
}

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_DIGITS = 3
