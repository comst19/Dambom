package com.comst19.dambom.feature.library.player.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import com.comst19.dambom.feature.library.R

@Composable
internal fun PlaybackOptionsButton(player: Player) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.Tune, null)
            Text(stringResource(R.string.player_settings))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            PlaybackOptionsMenuItems(player)
        }
    }
}
