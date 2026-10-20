package com.comst19.dambom.feature.library.player.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.comst19.dambom.core.domain.model.SUPPORTED_PLAYBACK_SPEEDS
import com.comst19.dambom.feature.library.R

@Composable
internal fun PlaybackOptionsMenuItems(player: Player) {
    var speed by remember(player) { mutableFloatStateOf(player.playbackParameters.speed) }
    var repeat by remember(player) { mutableStateOf(player.repeatMode == Player.REPEAT_MODE_ONE) }
    var choosingSpeed by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    speed = playbackParameters.speed
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    repeat = repeatMode == Player.REPEAT_MODE_ONE
                }
            }
        player.addListener(listener)
        speed = player.playbackParameters.speed
        repeat = player.repeatMode == Player.REPEAT_MODE_ONE
        onDispose { player.removeListener(listener) }
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.player_speed_value, speedLabel(speed))) },
        leadingIcon = { Icon(Icons.Outlined.Speed, null) },
        onClick = { choosingSpeed = true },
    )
    DropdownMenuItem(
        modifier =
            Modifier.semantics {
                role = Role.Checkbox
                toggleableState = if (repeat) ToggleableState.On else ToggleableState.Off
            },
        text = { Text(stringResource(R.string.player_repeat_one)) },
        leadingIcon = { Icon(Icons.Outlined.RepeatOne, null) },
        trailingIcon = { Checkbox(checked = repeat, onCheckedChange = null) },
        onClick = { player.repeatMode = if (repeat) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE },
    )
    if (choosingSpeed) {
        PlaybackSpeedDialog(
            speed = speed,
            onSelected = { player.setPlaybackSpeed(it) },
            onDismiss = { choosingSpeed = false },
        )
    }
}

@Composable
private fun PlaybackSpeedDialog(
    speed: Float,
    onSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_speed)) },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                SUPPORTED_PLAYBACK_SPEEDS.forEach { value ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = speed == value,
                            role = Role.RadioButton,
                            onClick = {
                                onSelected(value)
                                onDismiss()
                            },
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = speed == value, onClick = null)
                        Text(stringResource(R.string.player_speed_multiplier, speedLabel(value)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_options_close)) }
        },
    )
}

private fun speedLabel(speed: Float) = speed.toString().removeSuffix(".0")
