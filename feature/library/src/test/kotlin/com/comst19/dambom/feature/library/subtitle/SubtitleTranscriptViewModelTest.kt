package com.comst19.dambom.feature.library.subtitle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.LibraryTestDownloads
import com.comst19.dambom.feature.library.libraryTestVideo
import com.comst19.dambom.feature.library.subtitle.data.SubtitleFiles
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleTranscriptViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val temporary = TemporaryFolder()

    @Test fun `transcript grouping waits for computation dispatcher and preserves every cue`() =
        runTest(main.dispatcher) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val source = temporary.newFile("video.mp4")
            val files = SubtitleFiles(context, main.dispatcher)
            val cues = (0 until 1_000).map { SubtitleCue(it * 1_000L, it * 1_000L + 900, "Cue $it") }
            files.save(source.path, cues)
            val computation = TestCoroutineScheduler()
            val model =
                SubtitleTranscriptViewModel(
                    LibraryTestDownloads(listOf(libraryTestVideo(source.path))),
                    files,
                    SpyNavigationDispatcher(),
                    StandardTestDispatcher(computation),
                )
            model.load("saved-video")
            runCurrent()
            assertTrue(model.state.value.loading)
            assertTrue(
                model.state.value.sections
                    .isEmpty(),
            )
            computation.runCurrent()
            runCurrent()
            assertFalse(model.state.value.loading)
            assertFalse(model.state.value.failed)
            assertEquals(cues.size, model.state.value.cueCount)
            assertEquals(
                cues,
                model.state.value.sections
                    .flatMap { it.cues },
            )
        }
}
