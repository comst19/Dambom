package com.comst19.dambom.feature.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.component.LibraryFileActions
import com.comst19.dambom.feature.library.component.rememberLibraryFileActions
import com.comst19.dambom.feature.library.export.ExportDocumentStore
import com.comst19.dambom.feature.library.external.videoShareIntent
import com.comst19.dambom.feature.library.file.LibraryFileManager
import com.comst19.dambom.feature.library.file.RecordingFileProvider
import com.comst19.dambom.feature.library.file.ShareableVideo
import com.comst19.dambom.feature.library.file.registerTestFileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryVideoShareTest {
    @get:Rule val compose = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val createdFiles = mutableListOf<File>()
    private lateinit var provider: RecordingFileProvider

    @Before
    fun setUp() {
        provider = registerTestFileProvider(application)
    }

    @After
    fun tearDown() {
        createdFiles.forEach(File::delete)
        createdFiles.clear()
    }

    @Test
    fun `video share intent attaches matching stream and clip data uri with read and new task flags`() {
        val file = createAllowedVideo("clip.mp4")
        val contentUri = "content://${application.packageName}.fileprovider/saved_videos/${file.name}"
        val video = ShareableVideo(contentUri = contentUri, mimeType = "video/mp4", title = "Clip")

        val chooser = videoShareIntent(application, video, "Share video")
        val sendIntent = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)
        val expectedUri = contentUri.toUri()

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Share video", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE))
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNotNull(sendIntent)
        assertEquals(Intent.ACTION_SEND, sendIntent?.action)
        assertEquals("video/mp4", sendIntent?.type)
        assertEquals(
            expectedUri,
            IntentCompat.getParcelableExtra(checkNotNull(sendIntent), Intent.EXTRA_STREAM, Uri::class.java),
        )
        assertEquals(1, sendIntent.clipData?.itemCount)
        assertEquals(expectedUri, sendIntent.clipData?.getItemAt(0)?.uri)
        assertTrue(sendIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `sharing an allowed video queries provider off main and launches chooser with expected flags`() {
        val file = createAllowedVideo("eligible.mp4")
        val expectedUri = "content://${application.packageName}.fileprovider/saved_videos/${file.name}".toUri()
        val task = libraryTestVideo(file.path).copy(title = "Eligible video")
        val events = AppEventBus()
        val viewModel = createViewModel(tasks = listOf(task), events = events)
        lateinit var actions: LibraryFileActions
        compose.setContent {
            actions = rememberLibraryFileActions(remember { viewModel })
        }

        compose.runOnIdle { actions.onShareVideo(task) }
        compose.waitUntil { shadowOf(application).peekNextStartedActivity() != null }

        val started = shadowOf(application).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_CHOOSER, started?.action)
        assertTrue(checkNotNull(started).flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        val sendIntent = IntentCompat.getParcelableExtra(started, Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(sendIntent)
        assertEquals(Intent.ACTION_SEND, sendIntent?.action)
        assertEquals("video/mp4", sendIntent?.type)
        assertEquals(
            expectedUri,
            IntentCompat.getParcelableExtra(checkNotNull(sendIntent), Intent.EXTRA_STREAM, Uri::class.java),
        )
        assertEquals(1, sendIntent.clipData?.itemCount)
        assertEquals(expectedUri, sendIntent.clipData?.getItemAt(0)?.uri)
        assertTrue(sendIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(provider.getTypeThreads.isNotEmpty())
        assertTrue(provider.getTypeThreads.none { it === Looper.getMainLooper().thread })
    }

    @Test
    fun `private delete pending missing and outside provider files do not start sharing and notify failure`() {
        val allowedFile = createAllowedVideo("blocked.mp4")
        val outsideFile =
            application.cacheDir.resolve("outside-share.mp4").apply {
                writeBytes(byteArrayOf(1, 2, 3))
                createdFiles += this
            }
        val privateTask = libraryTestVideo(allowedFile.path).copy(id = "private", isPrivate = true)
        val deletePendingTask = libraryTestVideo(allowedFile.path).copy(id = "pending", deletePending = true)
        val missingFileTask = libraryTestVideo("/does/not/exist/missing.mp4").copy(id = "missing")
        val outsideTask = libraryTestVideo(outsideFile.path).copy(id = "outside")
        val events = AppEventBus()
        val viewModel =
            createViewModel(
                tasks = listOf(privateTask, deletePendingTask, missingFileTask, outsideTask),
                events = events,
            )
        lateinit var actions: LibraryFileActions
        compose.setContent {
            actions = rememberLibraryFileActions(remember { viewModel })
        }

        listOf(privateTask, deletePendingTask, missingFileTask, outsideTask).forEach { blockedTask ->
            compose.runOnIdle { actions.onShareVideo(blockedTask) }
            compose.waitForIdle()
            assertNull(shadowOf(application).nextStartedActivity)
            assertEquals(
                AppEvent.ShowSnackbar(UiText.Resource(R.string.library_share_failure)),
                runBlocking { events.events.first() },
            )
        }
    }

    private fun createAllowedVideo(name: String): File =
        application.filesDir
            .resolve("videos")
            .apply { mkdirs() }
            .resolve(name)
            .apply {
                writeBytes(byteArrayOf(1, 2, 3))
                createdFiles += this
            }

    private fun createViewModel(
        tasks: List<DownloadTask>,
        events: AppEventBus,
    ): LibraryViewModel =
        LibraryViewModel(
            repository = LibraryTestDownloads(tasks),
            settingsRepository = LibraryTestSettings,
            navigation = SpyNavigationDispatcher(),
            savedStateHandle = SavedStateHandle(),
            fileManager =
                LibraryFileManager(
                    context = application,
                    ioDispatcher = Dispatchers.Unconfined,
                    documents = ExportDocumentStore(application, Dispatchers.Unconfined),
                ),
            appEventBus = events,
            playbackHistoryRepository = LibraryTestPlaybackHistory(),
            privateVideoSession = PrivateVideoSession(),
            defaultDispatcher = Dispatchers.Unconfined,
        )
}
