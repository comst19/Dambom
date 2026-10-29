package com.comst19.dambom.feature.library.subtitle.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.delay

@Composable
internal fun BoxScope.TranslatedSubtitleOverlay(
    player: Player,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onAvailability: (Boolean) -> Unit = {},
) {
    val currentOnAvailability by rememberUpdatedState(onAvailability)
    var uri by remember(player) { mutableStateOf(player.currentMediaItem?.localConfiguration?.uri) }
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var positionRevision by remember(player) { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    positionRevision++
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    uri = mediaItem?.localConfiguration?.uri
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val cues by rememberSubtitleCues(uri)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var text by remember(uri) { mutableStateOf("") }
    LaunchedEffect(uri, cues) { currentOnAvailability(cues.isNotEmpty()) }
    val timeline = remember(cues) { SubtitleTimeline(cues) }
    LaunchedEffect(player, timeline, lifecycle, enabled, playing, positionRevision) {
        text = ""
        if (enabled && cues.isNotEmpty()) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                do {
                    text = timeline.at(player.currentPosition)?.text.orEmpty()
                    if (playing) delay(SUBTITLE_TICK_MILLIS)
                } while (playing)
            }
        }
    }
    if (text.isNotBlank()) {
        Text(
            text,
            modifier =
                modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 64.dp)
                    .background(Color.Black.copy(alpha = SUBTITLE_BACKGROUND_ALPHA))
                    .padding(4.dp),
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private const val SUBTITLE_TICK_MILLIS = 100L
private const val SUBTITLE_BACKGROUND_ALPHA = 0.75f
