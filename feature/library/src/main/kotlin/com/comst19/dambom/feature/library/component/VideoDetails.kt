package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.ORIGINAL_QUALITY
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.formatBytes
import com.comst19.dambom.feature.library.media.LocalVideoMetadata
import com.comst19.dambom.feature.library.toTimeText
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun VideoDetails(
    task: DownloadTask,
    metadata: LocalVideoMetadata?,
    onOpenOriginal: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val unknown = stringResource(R.string.player_info_unknown)
    val downloadedAt =
        remember(task.updatedAtMillis, configuration, unknown) {
            formatDownloadedAt(task.updatedAtMillis, configuration.locales[0], unknown)
        }
    val resolution =
        metadata?.let { details ->
            if (details.width != null && details.height != null) "${details.width} × ${details.height}" else null
        }
    val duration = metadata?.durationMillis?.toTimeText() ?: unknown
    val quality =
        if (task.quality == ORIGINAL_QUALITY) {
            stringResource(R.string.player_quality_original)
        } else {
            task.quality
        }

    val videoQuality = listOfNotNull(resolution, quality).joinToString(" · ").ifEmpty { unknown }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.player_info_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                VideoMetadataItem(stringResource(R.string.player_info_duration), duration)
                VideoMetadataItem(stringResource(R.string.player_info_size), task.downloadedBytes.formatBytes())
                VideoMetadataItem(stringResource(R.string.player_info_video_quality), videoQuality)
                VideoMetadataItem(stringResource(R.string.player_info_downloaded_at), downloadedAt)
            }
        }
        VideoSourceCard(
            sourcePageUrl = task.sourcePageUrl,
            onOpenOriginal = onOpenOriginal,
        )
    }
}

internal fun formatDownloadedAt(
    timestampMillis: Long,
    locale: Locale,
    unknown: String,
): String =
    timestampMillis.takeIf { it >= MIN_VALID_DOWNLOAD_TIMESTAMP_MILLIS }?.let { timestamp ->
        DateFormat
            .getDateInstance(DateFormat.MEDIUM, locale)
            .format(Date(timestamp))
    } ?: unknown

private const val MIN_VALID_DOWNLOAD_TIMESTAMP_MILLIS = 946_684_800_000L

@Composable
private fun VideoMetadataItem(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
