package com.comst19.dambom.feature.detection.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.feature.detection.R

@Composable
internal fun UnsupportedDetectionContent(
    reason: UnsupportedReason,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.detection_not_found), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text(
            reason.description(),
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (reason.canContinueInWeb()) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.detection_open_web_hint),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpenWeb,
                modifier = Modifier.fillMaxWidth(),
                shape = DambomShapes.Control,
            ) {
                Text(stringResource(R.string.detection_open_web))
            }
        }
        TextButton(onClick = onRetry) { Text(stringResource(R.string.detection_retry)) }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun UnsupportedReason.description(): String =
    stringResource(
        when (this) {
            UnsupportedReason.INVALID_URL -> R.string.detection_invalid_url
            UnsupportedReason.ACCESS_RESTRICTED -> R.string.detection_access_restricted
            UnsupportedReason.NO_MEDIA -> R.string.detection_no_media
            UnsupportedReason.NETWORK_ERROR -> R.string.detection_network_error
            UnsupportedReason.UNSUPPORTED_FORMAT -> R.string.detection_unsupported_format
        },
    )

private fun UnsupportedReason.canContinueInWeb(): Boolean =
    this == UnsupportedReason.NO_MEDIA || this == UnsupportedReason.UNSUPPORTED_FORMAT
