package com.comst19.dambom.feature.detection.component

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.feature.detection.R

@Composable
internal fun CandidatePreviewDialog(
    candidate: MediaCandidate,
    onDismiss: () -> Unit,
) {
    var videoAspectRatio by remember(candidate.id) { mutableFloatStateOf(VIDEO_ASPECT_RATIO) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            val maxVideoHeight = (maxHeight - PREVIEW_ACTIONS_HEIGHT).coerceAtLeast(1.dp)
            val videoWidth = minOf(maxWidth, maxVideoHeight * videoAspectRatio)
            val videoHeight = videoWidth / videoAspectRatio
            Surface(
                modifier = Modifier.width(videoWidth),
                color = Color.Black,
                shape = DambomShapes.Card,
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().height(videoHeight)) {
                        AndroidView(
                            factory = { context ->
                                VideoView(context).apply {
                                    setVideoURI(Uri.parse(candidate.url))
                                    setOnPreparedListener { player ->
                                        if (player.videoWidth > 0 && player.videoHeight > 0) {
                                            videoAspectRatio = player.videoWidth.toFloat() / player.videoHeight
                                        }
                                        player.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                            onRelease = VideoView::stopPlayback,
                        )
                        FilledIconButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            colors =
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.64f),
                                    contentColor = Color.White,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.detection_close_preview),
                            )
                        }
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            candidate.title,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private const val VIDEO_ASPECT_RATIO = 16f / 9f
private val PREVIEW_ACTIONS_HEIGHT = 64.dp
