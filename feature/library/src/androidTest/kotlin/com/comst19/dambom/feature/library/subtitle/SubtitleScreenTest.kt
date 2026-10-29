package com.comst19.dambom.feature.library.subtitle

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.core.common.io.videoSubtitleFile
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.VideoPlayerScreen
import com.comst19.dambom.feature.library.component.LibraryFileActions
import com.comst19.dambom.feature.library.player.PlayerInteractionState
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleModelStatus
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair
import com.comst19.dambom.feature.library.subtitle.model.subtitleTranscriptSections
import com.comst19.dambom.feature.library.subtitle.model.toSrt
import com.comst19.dambom.feature.library.subtitle.playback.SubtitleCaption
import com.comst19.dambom.feature.library.subtitle.playback.SubtitleToggleButton
import com.comst19.dambom.feature.library.subtitle.playback.TranslatedSubtitleOverlay
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SubtitleScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val task =
        DownloadTask(
            "video",
            "https://example.test/v",
            "https://example.test",
            "Video",
            "video/mp4",
            null,
            1,
            "original",
            DownloadStatus.COMPLETED,
            null,
            "video.mp4",
            "/data/local/tmp/subtitle-fixture.mp4",
            1,
            1,
        )

    @Test fun downloadableModelWaitsForAnExplicitTapAndOffersOnlyTheRequestedPairs() {
        var downloads = 0
        var selected: SubtitleLanguagePair? = null
        compose.setContent {
            MaterialTheme {
                SubtitleScreen(
                    SubtitleState(task = task, model = SubtitleModelStatus.DOWNLOADABLE, localOperation = SubtitleOperation.IDLE),
                    {},
                    { selected = it },
                    { downloads++ },
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.waitForIdle()
        assertEquals(0, downloads)
        compose.onNodeWithText(context.getString(R.string.subtitle_ja_en)).performClick()
        assertEquals(SubtitleLanguagePair.JAPANESE_ENGLISH, selected)
        compose.onNodeWithText(context.getString(R.string.subtitle_download)).performScrollTo().performClick()
        assertEquals(1, downloads)
        capture("subtitle-download.png")
    }

    @Test fun unavailableModelExplainsTheDeviceLimitationAndDoesNotOfferDownload() {
        compose.setContent {
            MaterialTheme {
                SubtitleScreen(
                    SubtitleState(task = task, model = SubtitleModelStatus.UNAVAILABLE, localOperation = SubtitleOperation.IDLE),
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.subtitle_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.subtitle_download)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.subtitle_generate)).assertDoesNotExist()
        capture("subtitle-unavailable.png")
    }

    @Test fun activeGenerationPreventsLanguageChangesAndAllowsCancellation() {
        compose.setContent {
            MaterialTheme {
                SubtitleScreen(
                    SubtitleState(task = task, model = SubtitleModelStatus.AVAILABLE, localOperation = SubtitleOperation.GENERATING),
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.subtitle_ja_en)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.subtitle_cancel)).assertIsDisplayed()
    }

    @Test fun savedSubtitleIsRenderedAtTheCurrentPlayerPosition() {
        val video = File(context.cacheDir, "subtitle-preview.mp4").apply { writeText("test") }
        video.videoSubtitleFile().writeText(listOf(SubtitleCue(0, 800, "번역 확인")).toSrt())
        lateinit var player: ExoPlayer
        compose.runOnUiThread {
            player = ExoPlayer.Builder(context).build()
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(video)))
        }
        try {
            val interaction = PlayerInteractionState()
            compose.setContent {
                Box(Modifier.fillMaxSize()) {
                    TranslatedSubtitleOverlay(player, interaction.subtitlesEnabled) { interaction.hasSubtitles = it }
                    SubtitleToggleButton(interaction)
                }
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("번역 확인").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("번역 확인").assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.subtitle_hide)).performClick()
            compose.onNodeWithText("번역 확인").assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.subtitle_show)).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("번역 확인").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("번역 확인").assertIsDisplayed()
            compose.runOnUiThread { player.seekTo(900) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("번역 확인").fetchSemanticsNodes().isEmpty() }
            compose.runOnUiThread { player.seekTo(0) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("번역 확인").fetchSemanticsNodes().isNotEmpty() }
            capture("subtitle-overlay.png")
            compose.runOnUiThread { player.clearMediaItems() }
            compose.waitUntil(5_000) { !interaction.hasSubtitles }
            compose.onNodeWithText("번역 확인").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { player.release() }
            video.videoSubtitleFile().delete()
            video.delete()
        }
    }

    @Test fun completedSubtitleAppearsWithoutReopeningThePlayer() {
        val video = File(context.cacheDir, "subtitle-late.mp4").apply { writeText("test") }
        lateinit var player: ExoPlayer
        compose.runOnUiThread {
            player = ExoPlayer.Builder(context).build()
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(video)))
        }
        try {
            compose.setContent { Box(Modifier.fillMaxSize()) { TranslatedSubtitleOverlay(player) } }
            compose.waitForIdle()
            compose.onNodeWithText("늦게 완성된 자막").assertDoesNotExist()
            video.videoSubtitleFile().writeText(listOf(SubtitleCue(0, 800, "늦게 완성된 자막")).toSrt())
            compose.waitUntil(5_000) { compose.onAllNodesWithText("늦게 완성된 자막").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            compose.runOnUiThread { player.release() }
            video.videoSubtitleFile().delete()
            video.delete()
        }
    }

    @Test fun exportingShowsSavingRatherThanGeneration() {
        compose.setContent {
            MaterialTheme {
                SubtitleScreen(
                    SubtitleState(task = task, model = SubtitleModelStatus.AVAILABLE, localOperation = SubtitleOperation.EXPORTING),
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.subtitle_exporting)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.subtitle_generating)).assertDoesNotExist()
    }

    @Test fun savedSubtitlesOfferAReaderWithoutExpandingTheTranscriptOnTheGenerator() {
        val paragraph = "긴 자막 본문은 별도 화면에 표시합니다. ".repeat(80)
        var opened = 0
        compose.setContent {
            MaterialTheme {
                SubtitleScreen(
                    state =
                        SubtitleState(
                            task = task,
                            model = SubtitleModelStatus.AVAILABLE,
                            localOperation = SubtitleOperation.IDLE,
                            cues = persistentListOf(SubtitleCue(0, 8_000, paragraph)),
                        ),
                    onBack = {},
                    onSelect = {},
                    onDownload = {},
                    onRefresh = {},
                    onGenerate = {},
                    onCancel = {},
                    onExport = {},
                    onDeleteModel = {},
                    onViewSubtitles = { opened++ },
                )
            }
        }
        compose.onNodeWithText(paragraph).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.subtitle_auto_saved)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.subtitle_generate)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.subtitle_view)).performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test fun transcriptReaderScrollsToTheLastParagraphOfALongVideo() {
        val cues =
            (0 until 240).map { index ->
                SubtitleCue(index * 30_000L, index * 30_000L + 8_000, "번역 문단 $index. 긴 영상의 자막도 시간 순서대로 읽을 수 있습니다.")
            }
        compose.setContent {
            MaterialTheme {
                SubtitleTranscriptScreen(
                    SubtitleTranscriptState("긴 영상", subtitleTranscriptSections(cues), cues.size, loading = false),
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText(cues.first().text).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(cues.last().text))
        compose.onNodeWithText(cues.last().text).assertIsDisplayed()
    }

    @Test fun videoActionMenuIsBoundedAndReopensAtTheTopAfterScrolling() {
        var copied = 0
        val actions =
            LibraryFileActions(
                { _, _ -> },
                {},
                {},
                {},
                { copied++ },
                {},
                onDelete = {},
                onTrim = {},
                onCompress = {},
                onTranslate = {},
            )
        compose.setContent {
            MaterialTheme {
                com.comst19.dambom.feature.library.component
                    .VideoActionsButton(task, actions)
            }
        }
        val more = context.getString(R.string.library_more_actions, task.title)
        val first = context.getString(R.string.subtitle_title)
        val copy = context.getString(R.string.library_copy_link)
        compose.onNodeWithContentDescription(more).performClick()
        compose.onNodeWithText(first).assertIsDisplayed()
        val viewport = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue(viewport.height <= 360 * density)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(context.getString(R.string.library_delete)))
        compose.onNodeWithText(context.getString(R.string.library_delete)).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy))
        compose.onNodeWithText(copy).performClick()
        assertEquals(1, copied)
        compose.onNodeWithContentDescription(more).performClick()
        compose.onNodeWithText(first).assertIsDisplayed()
    }

    @Test fun captionToggleSurvivesPlayerItemResetAndFullscreenSwitch() {
        val video = File(context.cacheDir, "subtitle-toggle.mp4").apply { writeText("test") }
        video.videoSubtitleFile().writeText(listOf(SubtitleCue(0, 8_000, "저장된 자막")).toSrt())
        val fullscreen = mutableStateOf(false)
        lateinit var player: ExoPlayer
        compose.runOnUiThread {
            player = ExoPlayer.Builder(context).build()
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(video)))
        }
        val actions = LibraryFileActions({ _, _ -> }, {}, {}, {}, {}, {}, onDelete = {})
        try {
            compose.setContent {
                MaterialTheme {
                    VideoPlayerScreen(
                        task.copy(localFilePath = video.path),
                        player,
                        actions,
                        {},
                        true,
                        fullscreen.value,
                        { fullscreen.value = it },
                        {},
                    )
                }
            }
            val hide = context.getString(R.string.subtitle_hide)
            val show = context.getString(R.string.subtitle_show)
            compose.waitUntil(5_000) {
                compose
                    .onAllNodes(
                        androidx.compose.ui.test
                            .hasContentDescription(hide),
                    ).fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.runOnUiThread { player.clearMediaItems() }
            compose.onNodeWithContentDescription(hide).assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(show).assertIsDisplayed()
            compose.runOnUiThread { fullscreen.value = true }
            compose.onNodeWithContentDescription(show).assertIsDisplayed().performClick()
            compose.runOnUiThread { fullscreen.value = false }
            compose.onNodeWithContentDescription(hide).assertIsDisplayed()
        } finally {
            compose.runOnUiThread { player.release() }
            video.videoSubtitleFile().delete()
            video.delete()
        }
    }

    @Test fun captionStaysAtTheVideoBottomWithoutAnOpaqueTextBox() {
        val videoColor = Color(0xFF124F76)
        compose.setContent {
            Box(Modifier.size(320.dp, 180.dp).background(videoColor).testTag("subtitle-video")) {
                SubtitleCaption("짧은 자막")
            }
        }
        val video = compose.onNodeWithTag("subtitle-video").fetchSemanticsNode().boundsInRoot
        val caption = compose.onNodeWithText("짧은 자막").fetchSemanticsNode().boundsInRoot
        assertTrue(caption.top > video.center.y)
        assertTrue(caption.bottom < video.bottom)
        val pixels = compose.onNodeWithTag("subtitle-video").captureToImage().toPixelMap()
        val x = (caption.left - video.left + 2).toInt()
        val y = (caption.top - video.top + 2).toInt()
        assertEquals(videoColor.toArgb(), pixels[x, y].toArgb())
        capture("subtitle-caption-bottom.png")
    }

    @Test fun timedCuesAdvanceOnSeekWithoutRewritingTheSavedSrt() {
        val video = File(context.cacheDir, "subtitle-segments.mp4").apply { writeText("test") }
        val original = listOf(SubtitleCue(0, 1_300, "첫 문장입니다."), SubtitleCue(6_200, 8_000, "마지막 문장입니다.")).toSrt()
        video.videoSubtitleFile().writeText(original)
        lateinit var player: ExoPlayer
        compose.runOnUiThread {
            player = ExoPlayer.Builder(context).build()
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(video)))
        }
        try {
            compose.setContent { Box(Modifier.fillMaxSize()) { TranslatedSubtitleOverlay(player) } }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("첫 문장입니다.").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnUiThread { player.seekTo(7_500) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("마지막 문장입니다.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("첫 문장입니다.").assertDoesNotExist()
            assertEquals(original, video.videoSubtitleFile().readText())
        } finally {
            compose.runOnUiThread { player.release() }
            video.videoSubtitleFile().delete()
            video.delete()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
