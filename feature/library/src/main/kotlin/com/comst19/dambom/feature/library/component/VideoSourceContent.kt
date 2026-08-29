package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.VideoSourceKind
import com.comst19.dambom.feature.library.videoSourcePresentation

@Composable
internal fun VideoSourceCard(
    sourcePageUrl: String,
    onOpenOriginal: () -> Unit,
) {
    val source = videoSourcePresentation(sourcePageUrl)
    Surface(
        onClick = onOpenOriginal,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
                Text(
                    text = source.host,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            Text(
                text =
                    stringResource(
                        if (source.kind == VideoSourceKind.X) {
                            R.string.player_open_in_x
                        } else {
                            R.string.player_open_website
                        },
                    ),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
