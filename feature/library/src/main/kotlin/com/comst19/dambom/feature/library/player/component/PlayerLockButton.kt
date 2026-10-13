package com.comst19.dambom.feature.library.player.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.player.PlayerInteractionState

@Composable
internal fun PlayerLockButton(
    interaction: PlayerInteractionState,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(onClick = { interaction.locked = false }, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.player_unlock))
        }
    }
}
