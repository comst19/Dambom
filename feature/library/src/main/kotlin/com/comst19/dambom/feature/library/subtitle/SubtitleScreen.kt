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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.subtitle.component.SubtitleError
import com.comst19.dambom.feature.library.subtitle.component.SubtitleLanguageSelector
import com.comst19.dambom.feature.library.subtitle.component.SubtitleModelActions
import com.comst19.dambom.feature.library.subtitle.component.SubtitleModelManagement
import com.comst19.dambom.feature.library.subtitle.component.SubtitleProgress
import com.comst19.dambom.feature.library.subtitle.component.SubtitleResult
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleModelStatus
import com.comst19.dambom.feature.library.subtitle.model.ModelDownloadProgress
import com.comst19.dambom.feature.library.subtitle.model.ModelDownloadStage
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair
import com.comst19.dambom.feature.library.subtitle.work.SubtitleModelDownload
import kotlinx.collections.immutable.persistentListOf

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
    onViewSubtitles: () -> Unit = {},
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
            SubtitleResult(state, onExport, onViewSubtitles)
            if (state.cues.isNotEmpty()) {
                Text(stringResource(R.string.subtitle_new_generation), style = MaterialTheme.typography.titleSmall)
            }
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
                }
            }
            if (state.failed) SubtitleError(state)
            Text(
                stringResource(R.string.subtitle_experimental),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.model == SubtitleModelStatus.AVAILABLE && !state.busy) {
                SubtitleModelManagement(onDownload, onDeleteModel)
            }
        }
    }
}

@Preview(name = "Subtitle ready", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
private fun SubtitleReadyPreview() {
    SubtitlePreview(SubtitleState(model = SubtitleModelStatus.AVAILABLE, localOperation = SubtitleOperation.IDLE))
}

@Preview(name = "Subtitle saved", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
private fun SubtitleSavedPreview() {
    SubtitlePreview(
        SubtitleState(
            model = SubtitleModelStatus.AVAILABLE,
            localOperation = SubtitleOperation.IDLE,
            cues = persistentListOf(SubtitleCue(0, 8_000, "저장된 번역 자막")),
        ),
    )
}

@Preview(name = "Model download", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
private fun SubtitleDownloadPreview() {
    SubtitlePreview(
        SubtitleState(
            model = SubtitleModelStatus.DOWNLOADABLE,
            localOperation = SubtitleOperation.IDLE,
            modelDownload =
                SubtitleModelDownload(
                    true,
                    false,
                    false,
                    ModelDownloadProgress(1_300_000_000, 2_588_147_712, 95, ModelDownloadStage.DOWNLOADING),
                ),
        ),
    )
}

@Composable
private fun SubtitlePreview(state: SubtitleState) {
    DambomTheme {
        SubtitleScreen(
            state =
                state.copy(
                    task =
                        DownloadTask(
                            id = "preview",
                            url = "https://example.com/video.mp4",
                            sourcePageUrl = "https://example.com",
                            title = "여행 인터뷰",
                            mimeType = "video/mp4",
                            expectedBytes = 4_000_000,
                            downloadedBytes = 4_000_000,
                            quality = "Original",
                            status = DownloadStatus.COMPLETED,
                            failureReason = null,
                            localFileName = "video.mp4",
                            localFilePath = "/preview/video.mp4",
                            createdAtMillis = 1,
                            updatedAtMillis = 1,
                        ),
                ),
            onBack = {},
            onSelect = {},
            onDownload = {},
            onRefresh = {},
            onGenerate = {},
            onCancel = {},
            onExport = {},
            onDeleteModel = {},
        )
    }
}
