package com.comst19.dambom.feature.library.subtitle.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    source: SubtitleCueSource,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 20.dp,
    fontSize: TextUnit = 14.sp,
) {
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
    val cues = if (uri == source.uri) source.cues else emptyList()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var text by remember(uri, cues) { mutableStateOf("") }
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
    if (enabled && uri == source.uri && text.isNotBlank()) SubtitleCaption(text, modifier, bottomPadding, fontSize)
}

@Composable
internal fun BoxScope.SubtitleCaption(
    text: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 20.dp,
    fontSize: TextUnit = 14.sp,
) {
    Box(
        modifier
            .align(Alignment.BottomCenter)
            .padding(start = 12.dp, end = 12.dp, bottom = bottomPadding),
    ) {
        Text(
            text = text,
            modifier =
                Modifier
                    .background(Color.Black.copy(alpha = SUBTITLE_BACKGROUND_ALPHA), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            color = Color.White,
            fontSize = fontSize,
            lineHeight = fontSize * SUBTITLE_LINE_HEIGHT,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(name = "Bright inline", widthDp = 360, heightDp = 203)
@Preview(name = "Bright wide", widthDp = 800, heightDp = 450)
@Composable
private fun BrightSubtitleCaptionPreview() {
    SubtitleCaptionPreview(Color.White)
}

@Preview(name = "Dark inline", widthDp = 360, heightDp = 203)
@Preview(name = "Dark wide", widthDp = 800, heightDp = 450)
@Composable
private fun DarkSubtitleCaptionPreview() {
    SubtitleCaptionPreview(Color.Black)
}

@Composable
private fun SubtitleCaptionPreview(videoColor: Color) {
    Box(Modifier.fillMaxWidth().aspectRatio(SUBTITLE_PREVIEW_ASPECT).background(videoColor)) {
        SubtitleCaption("밝거나 어두운 영상에서도\n자막을 또렷하게 읽을 수 있습니다.")
    }
}

private const val SUBTITLE_TICK_MILLIS = 100L

private const val SUBTITLE_LINE_HEIGHT = 1.3f
private const val SUBTITLE_BACKGROUND_ALPHA = 0.8f
private const val SUBTITLE_PREVIEW_ASPECT = 16f / 9f
