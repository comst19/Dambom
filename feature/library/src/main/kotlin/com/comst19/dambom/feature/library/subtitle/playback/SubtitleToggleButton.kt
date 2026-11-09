package com.comst19.dambom.feature.library.subtitle.playback

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.comst19.dambom.core.designsystem.DambomIconTooltip
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.player.PlayerInteractionState

@Composable
internal fun SubtitleToggleButton(interaction: PlayerInteractionState) {
    if (!interaction.hasSubtitles) return
    val label = stringResource(if (interaction.subtitlesEnabled) R.string.subtitle_hide else R.string.subtitle_show)
    DambomIconTooltip(label) {
        IconToggleButton(
            checked = interaction.subtitlesEnabled,
            onCheckedChange = { interaction.subtitlesEnabled = it },
        ) {
            Icon(Icons.Outlined.ClosedCaption, contentDescription = label)
        }
    }
}
