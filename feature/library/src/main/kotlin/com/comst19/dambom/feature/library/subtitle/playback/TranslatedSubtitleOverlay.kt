package com.comst19.dambom.feature.library.subtitle.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.delay

@Composable
@Suppress("LongParameterList")
internal fun BoxScope.TranslatedSubtitleOverlay(
    player: Player,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 20.dp,
    fontSize: TextUnit = 14.sp,
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
    if (text.isNotBlank()) SubtitleCaption(text, modifier, bottomPadding, fontSize)
}

@Composable
internal fun BoxScope.SubtitleCaption(
    text: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 20.dp,
    fontSize: TextUnit = 14.sp,
) {
    Text(
        text = text,
        modifier =
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = bottomPadding),
        color = Color.White,
        fontSize = fontSize,
        lineHeight = fontSize * SUBTITLE_LINE_HEIGHT,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        style =
            MaterialTheme.typography.bodyMedium.copy(
                shadow =
                    Shadow(
                        color = Color.Black,
                        offset = Offset.Zero,
                        blurRadius = SUBTITLE_SHADOW_RADIUS,
                    ),
            ),
    )
}

@Preview(name = "Inline subtitle", widthDp = 360, heightDp = 203)
@Preview(name = "Wide subtitle", widthDp = 800, heightDp = 450)
@Composable
private fun SubtitleCaptionPreview() {
    Box(Modifier.fillMaxWidth().aspectRatio(SUBTITLE_PREVIEW_ASPECT).background(Color(SUBTITLE_PREVIEW_COLOR))) {
        SubtitleCaption("안녕하세요, 이것은 자막 번역 테스트입니다.")
    }
}

private const val SUBTITLE_TICK_MILLIS = 100L

private const val SUBTITLE_LINE_HEIGHT = 1.3f
private const val SUBTITLE_SHADOW_RADIUS = 4f
private const val SUBTITLE_PREVIEW_ASPECT = 16f / 9f
private const val SUBTITLE_PREVIEW_COLOR = 0xFF124F76
