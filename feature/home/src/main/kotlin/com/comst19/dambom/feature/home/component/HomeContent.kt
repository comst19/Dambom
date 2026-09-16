package com.comst19.dambom.feature.home.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.feature.home.R
import com.comst19.dambom.feature.home.contract.HomeDownloadSummary
import com.comst19.dambom.feature.home.contract.HomeUiState

@Composable
internal fun HomePrimarySection(
    uiState: HomeUiState,
    canUseInternet: Boolean,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
    onUseClipboardSuggestion: () -> Unit,
    onDismissClipboardSuggestion: () -> Unit,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val canAnalyze = uiState.isUrlValid && canUseInternet
    val descriptionSpacing = if (compactHeight) 4.dp else 8.dp
    val inputSpacing = if (compactHeight) 12.dp else 24.dp
    val actionSpacing = if (compactHeight) 8.dp else 12.dp

    Column(modifier) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(descriptionSpacing))
        Text(
            text = stringResource(R.string.home_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(inputSpacing))
        HomeUrlField(
            uiState = uiState,
            onUrlChange = onUrlChange,
            onPaste = onPaste,
            onSubmit = {
                if (canAnalyze) {
                    keyboard?.hide()
                    onAnalyze()
                }
            },
        )
        Spacer(Modifier.height(actionSpacing))
        Button(
            onClick = {
                keyboard?.hide()
                onAnalyze()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            enabled = canAnalyze,
            shape = DambomShapes.Control,
        ) {
            Text(stringResource(R.string.home_analyze), style = MaterialTheme.typography.labelLarge)
        }
        uiState.clipboardUrl?.let {
            Spacer(Modifier.height(20.dp))
            ClipboardSuggestion(
                url = it,
                onUse = onUseClipboardSuggestion,
                onDismiss = onDismissClipboardSuggestion,
            )
        }
    }
}

@Composable
private fun HomeUrlField(
    uiState: HomeUiState,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onSubmit: () -> Unit,
) {
    TextField(
        value = uiState.url,
        onValueChange = onUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.home_url_label)) },
        placeholder = { Text(stringResource(R.string.home_url_placeholder)) },
        singleLine = true,
        isError = uiState.url.isNotBlank() && !uiState.isUrlValid,
        supportingText =
            if (uiState.url.isNotBlank() && !uiState.isUrlValid) {
                { Text(stringResource(R.string.home_invalid_url)) }
            } else {
                null
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        trailingIcon = {
            TextButton(onClick = onPaste, modifier = Modifier.padding(end = 4.dp)) {
                Text(stringResource(R.string.home_paste))
            }
        },
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        shape = DambomShapes.Control,
    )
}

@Composable
internal fun HomeSupportingSection(
    downloadSummary: HomeDownloadSummary,
    canUseInternet: Boolean,
    onOpenWeb: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Card(
            onClick = onOpenWeb,
            enabled = canUseInternet,
            modifier = Modifier.fillMaxWidth(),
            shape = DambomShapes.Card,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(imageVector = Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.home_browse_web), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.home_web_prompt_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        }
        if (downloadSummary.isVisible) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.home_current_activity),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            HomeDownloadStatus(summary = downloadSummary, onClick = onOpenDownloads)
        }
    }
}

@Composable
private fun HomeDownloadStatus(
    summary: HomeDownloadSummary,
    onClick: () -> Unit,
) {
    val title =
        when {
            summary.activeCount > 0 -> stringResource(R.string.home_download_active, summary.activeCount)
            summary.pausedCount > 0 -> stringResource(R.string.home_download_paused, summary.pausedCount)
            else -> stringResource(R.string.home_download_failed, summary.failedCount)
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = DambomShapes.Card,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.home_download_open),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.activeCount > 0) {
                LinearProgressIndicator(
                    progress = { summary.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ClipboardSuggestion(
    url: String,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = DambomShapes.Card,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_clipboard_found), style = MaterialTheme.typography.titleMedium)
            Text(
                text = url,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onUse) { Text(stringResource(R.string.home_use_link)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dismiss)) }
            }
        }
    }
}
