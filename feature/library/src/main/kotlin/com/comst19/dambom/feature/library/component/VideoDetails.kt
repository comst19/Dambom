package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
) {
    var informationExpanded by rememberSaveable(task.id) { mutableStateOf(false) }
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
        VideoInformationCard(
            expanded = informationExpanded,
            information =
                VideoInformation(
                    duration = duration,
                    fileSize = task.downloadedBytes.formatBytes(),
                    videoQuality = videoQuality,
                    downloadedAt = downloadedAt,
                ),
            onToggle = { informationExpanded = !informationExpanded },
        )
        VideoSourceCard(
            sourcePageUrl = task.sourcePageUrl,
            onOpenOriginal = onOpenOriginal,
            onCopyLink = onCopyLink,
            onShareLink = onShareLink,
        )
    }
}

@Immutable
private data class VideoInformation(
    val duration: String,
    val fileSize: String,
    val videoQuality: String,
    val downloadedAt: String,
)

@Composable
private fun VideoInformationCard(
    expanded: Boolean,
    information: VideoInformation,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_info_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription =
                        stringResource(
                            if (expanded) R.string.player_info_collapse else R.string.player_info_expand,
                        ),
                )
            }
            if (expanded) {
                VideoMetadataItem(stringResource(R.string.player_info_duration), information.duration)
                VideoMetadataItem(stringResource(R.string.player_info_size), information.fileSize)
                VideoMetadataItem(stringResource(R.string.player_info_video_quality), information.videoQuality)
                VideoMetadataItem(stringResource(R.string.player_info_downloaded_at), information.downloadedAt)
            }
        }
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
