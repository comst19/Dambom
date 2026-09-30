package com.comst19.dambom.feature.library.trim.component

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.util.suspendRunCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
internal fun TrimFilmstrip(
    path: String,
    durationMillis: Long,
    range: ClosedFloatingPointRange<Float>,
    positionMillis: Long,
) {
    val frames by produceState<List<ImageBitmap?>>(emptyList(), path, durationMillis) {
        value = loadTrimFrames(path, durationMillis)
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(56.dp)) {
        Row(
            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            frames.forEach { frame ->
                Box(Modifier.weight(1f).fillMaxSize()) {
                    if (frame != null) Image(frame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
        Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
            val start = size.width * range.start / durationMillis
            val end = size.width * range.endInclusive / durationMillis
            drawRect(Color.Black.copy(alpha = 0.55f), size = Size(start, size.height))
            drawRect(
                Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(end, 0f),
                size = Size(size.width - end, size.height),
            )
            val stroke = 2.dp.toPx()
            drawRoundRect(
                color = accent,
                topLeft = Offset(start, stroke / 2f),
                size = Size((end - start).coerceAtLeast(stroke), size.height - stroke),
                cornerRadius = CornerRadius(5.dp.toPx()),
                style = Stroke(stroke),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val current = positionMillis.toFloat().coerceIn(range.start, range.endInclusive)
            val playhead = size.width * current / durationMillis
            drawLine(
                Color.Black.copy(alpha = 0.3f),
                Offset(playhead, -3.dp.toPx()),
                Offset(playhead, size.height + 3.dp.toPx()),
                4.dp.toPx(),
                StrokeCap.Round,
            )
            drawLine(
                Color.White,
                Offset(playhead, -3.dp.toPx()),
                Offset(playhead, size.height + 3.dp.toPx()),
                2.dp.toPx(),
                StrokeCap.Round,
            )
        }
    }
}

private const val FRAME_COUNT = 8
private const val FRAME_SIZE = 160

private const val MICROS_PER_MILLI = 1_000L

private suspend fun loadTrimFrames(
    path: String,
    durationMillis: Long,
): List<ImageBitmap?> =
    withContext(Dispatchers.IO) {
        suspendRunCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                List(FRAME_COUNT) { index ->
                    ensureActive()
                    val timeUs = durationMillis * MICROS_PER_MILLI * index / FRAME_COUNT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            FRAME_SIZE,
                            FRAME_SIZE,
                        )
                    } else {
                        retriever
                            .getFrameAtTime(
                                timeUs,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            )?.let { original ->
                                val scale = FRAME_SIZE.toFloat() / maxOf(original.width, original.height)
                                val scaled =
                                    Bitmap.createScaledBitmap(
                                        original,
                                        (original.width * scale).toInt().coerceAtLeast(1),
                                        (
                                            original.height *
                                                scale
                                        ).toInt().coerceAtLeast(1),
                                        true,
                                    )
                                if (scaled !== original) original.recycle()
                                scaled
                            }
                    }?.asImageBitmap()
                }
            } finally {
                retriever.release()
            }
        }.getOrDefault(emptyList())
    }
