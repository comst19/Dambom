package com.comst19.dambom.feature.library.trim.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.trim.contract.TrimSelection
import com.comst19.dambom.feature.library.trim.media.clippedMediaItem

@Composable
@UnstableApi
internal fun TrimPreview(
    path: String,
    selection: TrimSelection,
    enabled: Boolean,
) {
    val context = LocalContext.current.applicationContext
    val player =
        remember(context) {
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
            }
        }
    var isPlaying by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                }
            }
        player.addListener(listener)
        onDispose { player.release() }
    }
    LaunchedEffect(path, selection) {
        failed = false
        player.pause()
        player.setMediaItem(clippedMediaItem(path, selection))
        player.prepare()
    }
    LaunchedEffect(enabled) { if (!enabled) player.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    Column {
        ContentFrame(
            player = player,
            modifier = Modifier.fillMaxWidth().aspectRatio(PREVIEW_ASPECT_RATIO).background(Color.Black),
        )
        if (failed) Text(stringResource(R.string.player_error))
        OutlinedButton(
            onClick = {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.seekTo(0L)
                    player.play()
                }
            },
            enabled = enabled && !failed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (isPlaying) R.string.trim_pause else R.string.trim_preview))
        }
    }
}

private const val PREVIEW_ASPECT_RATIO = 16f / 9f
