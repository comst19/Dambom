package com.comst19.dambom.feature.library.subtitle.playback

import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.splitSubtitleCue

internal class SubtitleTimeline(
    cues: List<SubtitleCue>,
) {
    private val ordered = cues.flatMap(::splitSubtitleCue).sortedBy { it.startMillis }

    fun at(positionMillis: Long): SubtitleCue? {
        var low = 0
        var high = ordered.lastIndex
        while (low <= high) {
            val middle = low + (high - low) / 2
            if (ordered[middle].startMillis <= positionMillis) {
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return ordered.getOrNull(high)?.takeIf { positionMillis < it.endMillis }
    }
}
