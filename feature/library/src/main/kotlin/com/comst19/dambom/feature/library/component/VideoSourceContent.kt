package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.VideoSourceKind
import com.comst19.dambom.feature.library.VideoSourcePresentation
import com.comst19.dambom.feature.library.videoSourcePresentation

@Composable
internal fun VideoSourceCard(
    sourcePageUrl: String,
    onOpenOriginal: () -> Unit,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
) {
    val source = videoSourcePresentation(sourcePageUrl)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = DambomShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VideoSourceHeader(source)
            VideoSourceActions(source, onOpenOriginal, onCopyLink, onShareLink)
        }
    }
}

@Composable
private fun VideoSourceHeader(source: VideoSourcePresentation) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.player_source_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text =
                stringResource(
                    if (source.kind == VideoSourceKind.X) {
                        R.string.player_source_x_post
                    } else {
                        R.string.player_source_website
                    },
                ),
            style = MaterialTheme.typography.titleMedium,
        )
        source.host?.let { host ->
            Text(
                text = host,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun VideoSourceActions(
    source: VideoSourcePresentation,
    onOpenOriginal: () -> Unit,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
) {
    TextButton(onClick = onOpenOriginal, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
        Text(
            text =
                stringResource(
                    if (source.kind == VideoSourceKind.X) {
                        R.string.player_open_in_x
                    } else {
                        R.string.player_open_website
                    },
                ),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceLinkButton(
            label = stringResource(R.string.library_copy_link_short),
            icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            onClick = onCopyLink,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        SourceLinkButton(
            label = stringResource(R.string.library_share_link_short),
            icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
            onClick = onShareLink,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun SourceLinkButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        icon()
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}
