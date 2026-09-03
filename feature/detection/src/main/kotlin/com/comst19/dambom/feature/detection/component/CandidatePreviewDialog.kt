package com.comst19.dambom.feature.detection.component

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.feature.detection.R

@Composable
internal fun CandidatePreviewDialog(
    candidate: MediaCandidate,
    onDismiss: () -> Unit,
) {
    var videoAspectRatio by remember(candidate.id) { mutableFloatStateOf(VIDEO_ASPECT_RATIO) }
    var isPrepared by remember(candidate.id) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.detection_play_selected_quality),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                        Text(
                            text = candidate.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.detection_close_preview),
                        )
                    }
                }
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val videoWidth = minOf(maxWidth, maxHeight * videoAspectRatio)
                    val videoHeight = videoWidth / videoAspectRatio
                    Box(
                        modifier = Modifier.width(videoWidth).height(videoHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        AndroidView(
                            factory = { context ->
                                val controller = MediaController(context)
                                VideoView(context).apply {
                                    setMediaController(controller)
                                    controller.setAnchorView(this)
                                    setVideoURI(Uri.parse(candidate.url))
                                    setOnPreparedListener { player ->
                                        if (player.videoWidth > 0 && player.videoHeight > 0) {
                                            videoAspectRatio = player.videoWidth.toFloat() / player.videoHeight
                                        }
                                        player.isLooping = true
                                        isPrepared = true
                                        start()
                                        controller.show()
                                    }
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                                    .alpha(if (isPrepared) 1f else 0f),
                            onRelease = VideoView::stopPlayback,
                        )
                        if (!isPrepared) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private const val VIDEO_ASPECT_RATIO = 16f / 9f
