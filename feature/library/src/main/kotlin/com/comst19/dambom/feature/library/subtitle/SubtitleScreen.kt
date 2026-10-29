@file:Suppress("MagicNumber")

package com.comst19.dambom.feature.library.subtitle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.subtitle.component.SubtitleLanguageSelector
import com.comst19.dambom.feature.library.subtitle.component.SubtitleModelActions
import com.comst19.dambom.feature.library.subtitle.component.SubtitleProgress
import com.comst19.dambom.feature.library.subtitle.component.SubtitleResult
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleModelStatus
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
internal fun SubtitleScreen(
    state: SubtitleState,
    onBack: () -> Unit,
    onSelect: (SubtitleLanguagePair) -> Unit,
    onDownload: () -> Unit,
    onRefresh: () -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    onExport: () -> Unit,
    onDeleteModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(maxWidth = 720.dp, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.subtitle_title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.subtitle_back))
            }
        })
    }) { padding ->
        Column(
            modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(state.task?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.subtitle_experimental))
            SubtitleLanguageSelector(state.pair, !state.busy && state.task != null, onSelect)
            when {
                state.loadFailed -> {
                    Text(stringResource(R.string.player_load_error))
                }

                state.operation == SubtitleOperation.CHECKING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.subtitle_checking))
                }

                state.busy -> {
                    SubtitleProgress(state, onCancel)
                }

                else -> {
                    SubtitleModelActions(state, onDownload, onRefresh, onGenerate)
                    if (state.model == SubtitleModelStatus.AVAILABLE) {
                        OutlinedButton(onClick = onDeleteModel) { Text(stringResource(R.string.subtitle_delete_model)) }
                    }
                }
            }
            if (state.failed) Text(stringResource(R.string.subtitle_failed), color = MaterialTheme.colorScheme.error)
            SubtitleResult(state, onExport)
        }
    }
}
