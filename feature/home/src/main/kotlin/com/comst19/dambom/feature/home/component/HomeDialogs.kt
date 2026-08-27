package com.comst19.dambom.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comst19.dambom.feature.home.R

@Composable
internal fun ClipboardConsentDialog(onDecision: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onDecision(false) },
        title = { Text(stringResource(R.string.home_clipboard_consent_title)) },
        text = { Text(stringResource(R.string.home_clipboard_consent_description)) },
        confirmButton = {
            TextButton(onClick = { onDecision(true) }) {
                Text(stringResource(R.string.home_clipboard_consent_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDecision(false) }) {
                Text(stringResource(R.string.home_clipboard_consent_deny))
            }
        },
    )
}

@Composable
internal fun SharedUrlDialog(
    url: String,
    onAnalyze: () -> Unit,
    onDismiss: () -> Unit,
    canUseInternet: Boolean,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_shared_url_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_shared_url_description))
                Text(
                    url,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAnalyze, enabled = canUseInternet) { Text(stringResource(R.string.home_analyze_now)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) }
        },
    )
}
