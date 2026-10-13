package com.comst19.dambom.feature.library.player.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomIconTooltip
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.component.FullscreenContentMode
import com.comst19.dambom.feature.library.player.PlayerInteractionState

@Composable
internal fun PlayerSettingsButton(
    interaction: PlayerInteractionState,
    contentMode: FullscreenContentMode,
    onContentModeToggle: () -> Unit,
    onRotate: (() -> Unit)?,
    onExpandedChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    fun close() {
        expanded = false
        onExpandedChange(false)
    }
    Box {
        DambomIconTooltip(stringResource(R.string.player_settings)) {
            IconButton(onClick = {
                expanded = true
                onExpandedChange(true)
            }) {
                Icon(Icons.Outlined.Tune, stringResource(R.string.player_settings), tint = Color.White)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = ::close, modifier = Modifier.width(220.dp)) {
            val isFit = contentMode == FullscreenContentMode.Fit
            PlaybackMenuItem(
                if (isFit) Icons.Outlined.CropFree else Icons.Outlined.ZoomOutMap,
                stringResource(if (isFit) R.string.player_expand_crop else R.string.player_fit_video),
                stringResource(if (isFit) R.string.player_fill_hint else R.string.player_fit_hint),
            ) {
                close()
                onContentModeToggle()
            }
            onRotate?.let { rotate ->
                PlaybackMenuItem(Icons.Outlined.Rotate90DegreesCw, stringResource(R.string.player_rotate)) {
                    close()
                    rotate()
                }
            }
            if (interaction.scale > 1f) {
                PlaybackMenuItem(Icons.Outlined.RestartAlt, stringResource(R.string.player_zoom_reset)) {
                    close()
                    interaction.resetZoom()
                }
            }
            PlaybackMenuItem(
                Icons.Outlined.Lock,
                stringResource(R.string.player_lock),
                stringResource(R.string.player_lock_hint),
            ) {
                close()
                interaction.locked = true
            }
        }
    }
}

@Composable
private fun PlaybackMenuItem(
    icon: ImageVector,
    label: String,
    hint: String = label,
    onClick: () -> Unit,
) {
    DambomIconTooltip(hint) {
        DropdownMenuItem(
            text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
            onClick = onClick,
        )
    }
}
