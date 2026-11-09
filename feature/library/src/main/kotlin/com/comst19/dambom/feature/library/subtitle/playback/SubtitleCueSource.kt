package com.comst19.dambom.feature.library.subtitle.playback

import android.net.Uri
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue

internal data class SubtitleCueSource(
    val uri: Uri?,
    val cues: List<SubtitleCue>,
)
