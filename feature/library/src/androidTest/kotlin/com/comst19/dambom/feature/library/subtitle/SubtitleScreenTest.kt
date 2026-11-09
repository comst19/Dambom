package com.comst19.dambom.feature.library.subtitle

import android.graphics.Bitmap
import android.net.Uri
import android.os.FileObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.comst19.dambom.feature.library.subtitle.playback.SubtitleCueSource
import com.comst19.dambom.feature.library.subtitle.playback.SubtitleToggleButton
import com.comst19.dambom.feature.library.subtitle.playback.TranslatedSubtitleOverlay
import com.comst19.dambom.feature.library.subtitle.playback.rememberSubtitleCues
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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

    @Test fun languageSelectionPreparesOnlyAfterAnExplicitDownloadTap() {
        var downloads = 0
        val selected = mutableStateOf(SubtitleLanguagePair.ENGLISH_KOREAN)
        compose.setContent {
            MaterialTheme {
                SubtitleScreen(
                    SubtitleState(
                        task = task,
                        pair = selected.value,
                        model = SubtitleModelStatus.DOWNLOADABLE,
                        localOperation = SubtitleOperation.IDLE,
                    ),
                    {},
                    { selected.value = it },
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
        val locale = context.resources.configuration.locales[0]
        val french =
            java.util.Locale
                .forLanguageTag("fr-FR")
                .getDisplayName(locale)
        val urdu =
            java.util.Locale
                .forLanguageTag("ur")
                .getDisplayName(locale)
        compose.onNodeWithText(context.getString(R.string.subtitle_source_language), substring = true).performClick()
        compose.onNodeWithTag("subtitle-language-options").performScrollToNode(hasText(french))
        compose.onNodeWithText(french).performClick()
        compose.onNodeWithText(context.getString(R.string.subtitle_target_language), substring = true).performClick()
        compose.onNodeWithTag("subtitle-language-options").performScrollToNode(hasText(urdu))
        compose.onNodeWithText(urdu).performClick()
        assertEquals(SubtitleLanguagePair("fr-FR", "ur"), selected.value)
        assertEquals(0, downloads)
        compose.onNodeWithText(context.getString(R.string.subtitle_download)).performScrollTo().performClick()
        assertEquals(1, downloads)
        capture("subtitle-download.png")
    }

    @Test fun emptyGenerationShowsANeutralResultAndTheAccuracyNotice() {
        compose.setContent {
            MaterialTheme {
                SubtitleScreen(
                    state =
                        SubtitleState(
                            task = task,
                            model = SubtitleModelStatus.AVAILABLE,
                            localOperation = SubtitleOperation.IDLE,
                            generation =
                                com.comst19.dambom.feature.library.subtitle.work.SubtitleJob(
                                    active = false,
                                    completed = true,
                                    failed = false,
                                    pair = SubtitleLanguagePair.ENGLISH_KOREAN,
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
        compose.onNodeWithText(context.getString(R.string.subtitle_no_speech)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.subtitle_experimental)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.subtitle_failed)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.subtitle_export)).assertDoesNotExist()
        capture("subtitle-empty.png")
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
        compose.onNodeWithText(context.getString(R.string.subtitle_source_language), substring = true).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.subtitle_target_language), substring = true).assertIsNotEnabled()
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
                    val cues by rememberSubtitleCues(Uri.fromFile(video))
                    SideEffect { interaction.hasSubtitles = cues.isNotEmpty() }
                    TranslatedSubtitleOverlay(player, SubtitleCueSource(Uri.fromFile(video), cues), interaction.subtitlesEnabled)
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
            compose.waitForIdle()
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
            compose.setContent {
                Box(Modifier.fillMaxSize()) {
                    val cues by rememberSubtitleCues(Uri.fromFile(video))
                    TranslatedSubtitleOverlay(player, SubtitleCueSource(Uri.fromFile(video), cues))
                }
            }
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

    @Test fun captionHasWhiteTextOnEightyPercentBlackOverBrightVideo() {
        assertCaptionContrast(Color.White, Color(0xFF333333), "bright")
    }

    @Test fun captionHasWhiteTextOnEightyPercentBlackOverDarkVideo() {
        assertCaptionContrast(Color.Black, Color.Black, "dark")
    }

    private fun assertCaptionContrast(
        videoColor: Color,
        expectedBackground: Color,
        name: String,
    ) {
        val text = "첫 번째 줄\n두 번째 줄"
        compose.setContent {
            Box(Modifier.size(320.dp, 180.dp).background(videoColor).testTag("subtitle-video")) {
                SubtitleCaption(text)
            }
        }
        val video = compose.onNodeWithTag("subtitle-video").fetchSemanticsNode().boundsInRoot
        val caption = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        assertTrue(caption.top > video.center.y)
        assertTrue(caption.bottom < video.bottom)
        assertTrue(caption.width < video.width)
        val pixels = compose.onNodeWithText(text).captureToImage().toPixelMap()
        val background = pixels[2, pixels.height / 2]
        assertEquals(expectedBackground.red, background.red, 0.01f)
        assertEquals(expectedBackground.green, background.green, 0.01f)
        assertEquals(expectedBackground.blue, background.blue, 0.01f)
        assertTrue(
            (0 until pixels.height).any { y ->
                (0 until pixels.width).any { x -> pixels[x, y].toArgb() == Color.White.toArgb() }
            },
        )
        val videoPixels = compose.onNodeWithTag("subtitle-video").captureToImage().toPixelMap()
        assertEquals(videoColor.toArgb(), videoPixels[2, videoPixels.height / 2].toArgb())
        capture("subtitle-caption-$name.png")
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
            compose.setContent {
                Box(Modifier.fillMaxSize()) {
                    val cues by rememberSubtitleCues(Uri.fromFile(video))
                    TranslatedSubtitleOverlay(player, SubtitleCueSource(Uri.fromFile(video), cues))
                }
            }
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

    @Test fun parentSharesOneSrtReadAcrossInlineFullscreenAndPipContent() {
        val video = File(context.cacheDir, "subtitle-shared.mp4").apply { writeText("test") }
        val subtitle =
            video.videoSubtitleFile().apply {
                writeText(listOf(SubtitleCue(0, 8_000, "공유 자막")).toSrt())
            }
        val reads = AtomicInteger()
        val observer =
            object : FileObserver(subtitle, CLOSE_NOWRITE) {
                override fun onEvent(
                    event: Int,
                    path: String?,
                ) {
                    if (event and CLOSE_NOWRITE != 0) reads.incrementAndGet()
                }
            }
        lateinit var player: ExoPlayer
        compose.runOnUiThread {
            player = ExoPlayer.Builder(context).build()
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(video)))
        }
        val fullscreen = mutableStateOf(false)
        val pip = mutableStateOf(false)
        observer.startWatching()
        try {
            compose.setContent {
                MaterialTheme {
                    VideoPlayerScreen(
                        task = task.copy(localFilePath = video.path),
                        player = player,
                        fileActions = LibraryFileActions({ _, _ -> }, {}, {}, {}, {}, {}, onDelete = {}),
                        onBack = {},
                        showBack = true,
                        isVideoFullscreen = fullscreen.value,
                        onVideoFullscreenChange = { fullscreen.value = it },
                        onVideoRotate = {},
                        isPipContentOnly = pip.value,
                    )
                }
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("공유 자막").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, reads.get())
            compose.runOnIdle { fullscreen.value = true }
            compose.onNodeWithText("공유 자막").assertIsDisplayed()
            compose.runOnIdle { pip.value = true }
            compose.onNodeWithText("공유 자막").assertDoesNotExist()
            compose.runOnIdle {
                pip.value = false
                fullscreen.value = false
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("공유 자막").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, reads.get())
            subtitle.writeText(listOf(SubtitleCue(0, 8_000, "새로 생성한 공유 자막")).toSrt())
            compose.waitUntil(5_000) { compose.onAllNodesWithText("새로 생성한 공유 자막").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(2, reads.get())
            subtitle.delete()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("새로 생성한 공유 자막").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithContentDescription(context.getString(R.string.subtitle_hide)).assertDoesNotExist()
        } finally {
            observer.stopWatching()
            compose.runOnUiThread { player.release() }
            subtitle.delete()
            video.delete()
        }
    }

    @Test fun sharedCuesAreHiddenUntilThePlayerMatchesTheirVideo() {
        val first = Uri.parse("file:///first.mp4")
        val second = Uri.parse("file:///second.mp4")
        val source = mutableStateOf(SubtitleCueSource(first, listOf(SubtitleCue(0, 8_000, "이전 영상"))))
        lateinit var player: ExoPlayer
        compose.runOnUiThread {
            player = ExoPlayer.Builder(context).build()
            player.setMediaItem(MediaItem.fromUri(first))
        }
        try {
            compose.setContent {
                Box(Modifier.fillMaxSize()) { TranslatedSubtitleOverlay(player, source.value) }
            }
            compose.onNodeWithText("이전 영상").assertIsDisplayed()
            compose.runOnIdle {
                source.value = SubtitleCueSource(second, listOf(SubtitleCue(0, 8_000, "다음 영상")))
            }
            compose.onNodeWithText("이전 영상").assertDoesNotExist()
            compose.onNodeWithText("다음 영상").assertDoesNotExist()
            compose.runOnUiThread { player.setMediaItem(MediaItem.fromUri(second)) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("다음 영상").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnUiThread { player.setMediaItem(MediaItem.fromUri(first)) }
            compose.onNodeWithText("다음 영상").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { player.release() }
        }
    }

    @Test fun changingTheSubtitleUriPublishesEmptyBeforeTheNewFileIsRead() {
        val first = File(context.cacheDir, "subtitle-source-first.mp4")
        val second = File(context.cacheDir, "subtitle-source-second.mp4")
        first.videoSubtitleFile().writeText(listOf(SubtitleCue(0, 800, "이전 자막")).toSrt())
        val uri = mutableStateOf(Uri.fromFile(first))
        val observed = mutableListOf<Pair<Uri, List<SubtitleCue>>>()
        try {
            compose.setContent {
                val cues = rememberSubtitleCues(uri.value).value
                SideEffect { observed.add(uri.value to cues) }
            }
            compose.waitUntil(5_000) { observed.any { it.second.isNotEmpty() } }
            compose.runOnIdle { uri.value = Uri.fromFile(second) }
            compose.runOnIdle {
                val newSourceStates = observed.filter { it.first == Uri.fromFile(second) }
                assertTrue(newSourceStates.isNotEmpty())
                assertTrue(newSourceStates.all { it.second.isEmpty() })
            }
        } finally {
            first.videoSubtitleFile().delete()
            second.videoSubtitleFile().delete()
        }
    }

    @Test fun resumeRefreshesSameStampSubtitlesAndRestartObservesTheFileAgain() {
        val video = File(context.cacheDir, "subtitle-lifecycle.mp4")
        val subtitle =
            video.videoSubtitleFile().apply {
                writeText(listOf(SubtitleCue(0, 800, "AAA")).toSrt())
            }
        val owner =
            object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this)
            }
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    val cues by rememberSubtitleCues(Uri.fromFile(video))
                    Text(cues.joinToString { it.text })
                }
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("AAA").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.STARTED }
            val stamp = subtitle.lastModified()
            subtitle.writeText(listOf(SubtitleCue(0, 800, "BBB")).toSrt())
            assertTrue(subtitle.setLastModified(stamp))
            compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("BBB").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.CREATED }
            subtitle.writeText(listOf(SubtitleCue(0, 800, "CCC")).toSrt())
            compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("CCC").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.DESTROYED }
            subtitle.delete()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
