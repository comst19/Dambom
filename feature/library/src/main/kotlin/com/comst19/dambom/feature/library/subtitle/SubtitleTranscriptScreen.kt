@file:Suppress("MagicNumber")

package com.comst19.dambom.feature.library.subtitle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.subtitleTranscriptSections
import com.comst19.dambom.feature.library.toTimeText

@Composable
internal fun SubtitleTranscriptRoute(
    id: String,
    viewModel: SubtitleTranscriptViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    SubtitleTranscriptScreen(state, viewModel::goBack)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SubtitleTranscriptScreen(
    state: SubtitleTranscriptState,
    onBack: () -> Unit,
) {
    AppScreen(maxWidth = 720.dp, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.subtitle_view)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.subtitle_transcript_back))
            }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("summary") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.subtitle_transcript_count, state.cueCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                state.loading -> item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.failed -> item("failure") { Text(stringResource(R.string.player_load_error)) }
                state.sections.isEmpty() -> item("empty") { Text(stringResource(R.string.subtitle_transcript_empty)) }
            }
            state.sections.forEach { section ->
                stickyHeader(key = "section:${section.startMillis}") {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                section.startMillis.toTimeText(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            HorizontalDivider()
                        }
                    }
                }
                itemsIndexed(section.cues, key = { index, _ -> "cue:${section.startMillis}:$index" }) { _, cue ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${cue.startMillis.toTimeText()} - ${cue.endMillis.toTimeText()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SelectionContainer { Text(cue.text, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
        }
    }
}

@Preview(name = "Subtitle transcript", widthDp = 360, heightDp = 800, locale = "ko")
@Preview(name = "Subtitle transcript tablet", widthDp = 800, heightDp = 800, locale = "ko")
@Composable
private fun SubtitleTranscriptPreview() {
    val cues =
        listOf(
            SubtitleCue(0, 8_000, "안녕하세요. 오늘은 여행 중 기억에 남았던 이야기를 나눠 보려고 해요. 천천히 걸으며 주변을 둘러보니 평소 놓쳤던 풍경들이 눈에 들어왔습니다."),
            SubtitleCue(8_000, 16_000, "가장 좋았던 순간은 작은 카페에서 잠시 쉬었던 시간이었어요. 창밖으로 사람들이 지나가는 모습을 보며 다음 목적지를 정했습니다."),
            SubtitleCue(60_000, 68_000, "다음 구간에서는 여행을 준비하며 알게 된 내용을 소개할게요. 필요한 물건을 챙기고 여유 있게 출발하는 것이 좋습니다."),
        )
    DambomTheme {
        SubtitleTranscriptScreen(
            SubtitleTranscriptState("여행 인터뷰", subtitleTranscriptSections(cues), cues.size, loading = false),
            onBack = {},
        )
    }
}
