package com.comst19.dambom.feature.library

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.PlaybackHistory
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import com.comst19.dambom.core.domain.repository.PlaybackHistoryRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.feature.library.player.PlaybackProgressSnapshot
import com.comst19.dambom.feature.library.player.VideoPlaybackController
import com.comst19.dambom.feature.library.player.VideoPlaybackListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelJvmTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun `clearing view model cancels preparation saves final history and releases player once in order`() =
        runTest(mainDispatcher.dispatcher) {
            val events = mutableListOf<String>()
            val controller =
                FakeVideoPlaybackController(events = events).apply {
                    durationMillis = 20_000L
                }
            val pendingHistory = CompletableDeferred<PlaybackHistory?>()
            val historyRepository =
                RecordingHistoryRepository(
                    onGet = { id ->
                        if (id == "second") pendingHistory.await() else null
                    },
                    onSave = { id, position, duration ->
                        events += "save:$id:$position/$duration"
                    },
                )
            val model = createViewModel(controller, historyRepository, backgroundScope)
            val store = bindToStore(model)

            model.prepare(savedVideo("first").copy(localFilePath = "/videos/first.mp4"))
            runCurrent()
            controller.positionMillis = 7_500L
            controller.play()
            controller.emitIsPlaying(true)
            runCurrent()
            controller.positionMillis = 12_000L
            events.clear()

            store.clear()
            runCurrent()

            assertEquals(listOf("save:first:12000/20000", "pause", "release"), events)
            assertEquals(1, controller.releaseCalls)

            val secondController = FakeVideoPlaybackController()
            val secondModel = createViewModel(secondController, historyRepository, backgroundScope)
            val secondStore = bindToStore(secondModel)
            secondModel.prepare(savedVideo("second").copy(localFilePath = "/videos/second.mp4"))
            runCurrent()
            secondStore.clear()
            pendingHistory.complete(PlaybackHistory("second", 4_000L, 20_000L, 1L))
            runCurrent()

            assertNull(secondController.currentMediaId)
            assertEquals(1, secondController.releaseCalls)
        }

    @Test
    fun `private lock clears active private playback and blocks subsequent prepare or play`() =
        runTest(mainDispatcher.dispatcher) {
            val controller = FakeVideoPlaybackController()
            val session = PrivateVideoSession()
            val model = createViewModel(controller, scope = backgroundScope, session = session)
            val store = bindToStore(model)
            val task = savedVideo("private").copy(localFilePath = "/private/clip.mp4", isPrivate = true)

            try {
                model.prepare(task)
                runCurrent()
                assertNull(controller.currentMediaId)

                session.unlock()
                model.prepare(task)
                runCurrent()
                assertEquals("private", controller.currentMediaId)
                assertEquals("/private/clip.mp4", controller.currentSourcePath)

                session.lock()
                runCurrent()
                assertNull(controller.currentMediaId)
                model.requestPlayPause()
                model.prepare(task)
                runCurrent()
                assertNull(controller.currentMediaId)
                assertFalse(controller.playWhenReady)
            } finally {
                store.clear()
            }
        }

    @Test
    fun `prepare deduplicates same id and path and replaces when path changes`() =
        runTest(mainDispatcher.dispatcher) {
            val controller = FakeVideoPlaybackController()
            val model = createViewModel(controller, scope = backgroundScope)
            val store = bindToStore(model)
            val task = savedVideo("clip").copy(localFilePath = "/videos/old.mp4")

            try {
                model.prepare(task)
                runCurrent()
                model.prepare(task.copy(isFavorite = true))
                runCurrent()
                assertEquals(1, controller.prepareCalls)

                model.prepare(task.copy(localFilePath = "/videos/new.mp4"))
                runCurrent()
                assertEquals(2, controller.prepareCalls)
                assertEquals("/videos/new.mp4", controller.currentSourcePath)
            } finally {
                store.clear()
            }
        }

    @Test
    fun `resume prompt pauses playback until answered and speed updates persist without echo`() =
        runTest(mainDispatcher.dispatcher) {
            val controller = FakeVideoPlaybackController()
            val settings = JvmSpeedSettings()
            val historyRepository =
                RecordingHistoryRepository(
                    onGet = { PlaybackHistory("clip", 6_000L, 20_000L, 1L) },
                )
            val model = createViewModel(controller, historyRepository, backgroundScope, settings = settings)
            val store = bindToStore(model)

            try {
                runCurrent()
                settings.settings.value = AppSettings(playbackSpeed = 1.5f)
                runCurrent()
                assertEquals(1.5f, controller.currentSpeed)
                assertTrue(settings.savedSpeeds.isEmpty())

                model.prepare(savedVideo("clip").copy(localFilePath = "/videos/clip.mp4"))
                runCurrent()
                model.requestPlayPause()
                assertEquals(6_000L, model.resumePromptPosition.value)
                assertFalse(controller.playWhenReady)

                model.answerResume(continueFromPosition = true)
                assertNull(model.resumePromptPosition.value)
                assertEquals(6_000L, controller.positionMillis)
                assertTrue(controller.playWhenReady)

                controller.setPlaybackSpeed(2.0f)
                runCurrent()
                assertEquals(listOf(2.0f), settings.savedSpeeds)
            } finally {
                store.clear()
            }
        }

    @Test
    fun `picture in picture stop keeps playing and exiting after stop pauses playback`() =
        runTest(mainDispatcher.dispatcher) {
            val controller = FakeVideoPlaybackController()
            val model = createViewModel(controller, scope = backgroundScope)
            val store = bindToStore(model)

            try {
                model.prepare(savedVideo("clip").copy(localFilePath = "/videos/clip.mp4"))
                runCurrent()
                model.requestPlayPause()
                assertTrue(controller.playWhenReady)

                model.onPictureInPictureEntered()
                model.onUiStopped()
                assertTrue(controller.playWhenReady)

                model.onPictureInPictureExited()
                assertFalse(controller.playWhenReady)
            } finally {
                store.clear()
            }
        }

    private fun createViewModel(
        controller: VideoPlaybackController,
        history: PlaybackHistoryRepository = LibraryTestPlaybackHistory(),
        scope: CoroutineScope,
        settings: SettingsRepository = LibraryTestSettings,
        session: PrivateVideoSession = PrivateVideoSession(),
    ): VideoPlayerViewModel =
        VideoPlayerViewModel(
            playback = controller,
            historyRepository = history,
            settingsRepository = settings,
            applicationScope = scope,
            appEventBus = AppEventBus(),
            privateVideoSession = session,
        )

    private fun bindToStore(viewModel: VideoPlayerViewModel): ViewModelStore =
        ViewModelStore().also { store ->
            val factory = viewModelFactory { initializer { viewModel } }
            ViewModelProvider.create(store, factory)[VideoPlayerViewModel::class]
        }
}

private class FakeVideoPlaybackController(
    private val events: MutableList<String> = mutableListOf(),
) : VideoPlaybackController {
    private var listener: VideoPlaybackListener? = null
    override var currentMediaId: String? = null
    override var currentSourcePath: String? = null
    override var playWhenReady: Boolean = false
    override var isPlaying: Boolean = false
    override var isEnded: Boolean = false
    var positionMillis: Long = 0L
    var durationMillis: Long = 10_000L
    var currentSpeed: Float = 1.0f
    var prepareCalls: Int = 0
    var releaseCalls: Int = 0

    override val currentProgress: PlaybackProgressSnapshot?
        get() = currentMediaId?.let { PlaybackProgressSnapshot(it, positionMillis, durationMillis) }

    override fun setListener(listener: VideoPlaybackListener?) {
        this.listener = listener
    }

    override fun prepare(
        mediaId: String,
        localFilePath: String,
        title: String,
    ) {
        prepareCalls++
        currentMediaId = mediaId
        currentSourcePath = localFilePath
        positionMillis = 0L
        isEnded = false
    }

    override fun play() {
        events += "play"
        playWhenReady = true
        listener?.onPlayWhenReadyChanged(true)
    }

    override fun pause() {
        events += "pause"
        playWhenReady = false
        isPlaying = false
        listener?.onPlayWhenReadyChanged(false)
    }

    override fun seekTo(positionMillis: Long) {
        this.positionMillis = positionMillis
    }

    override fun seekToDefaultPosition() {
        positionMillis = 0L
    }

    override fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        listener?.onPlaybackSpeedChanged(speed)
    }

    override fun clearMediaItems() {
        currentMediaId = null
        currentSourcePath = null
        positionMillis = 0L
    }

    override fun stopAndClear() {
        playWhenReady = false
        isPlaying = false
        clearMediaItems()
    }

    override fun release() {
        events += "release"
        releaseCalls++
        listener = null
    }

    fun emitIsPlaying(playing: Boolean) {
        isPlaying = playing
        listener?.onIsPlayingChanged(playing)
    }
}

private class RecordingHistoryRepository(
    private val onGet: suspend (String) -> PlaybackHistory? = { null },
    private val onSave: (String, Long, Long) -> Unit = { _, _, _ -> },
) : PlaybackHistoryRepository {
    override fun observeHistory(): Flow<Map<String, PlaybackHistory>> = MutableStateFlow(emptyMap())

    override suspend fun getHistory(downloadId: String): PlaybackHistory? = onGet(downloadId)

    override suspend fun saveProgress(
        downloadId: String,
        positionMillis: Long,
        durationMillis: Long,
        lastPlayedAtMillis: Long,
    ) {
        onSave(downloadId, positionMillis, durationMillis)
    }
}

private class JvmSpeedSettings : SettingsRepository by LibraryTestSettings {
    override val settings = MutableStateFlow(AppSettings())
    val savedSpeeds = mutableListOf<Float>()

    override suspend fun setPlaybackSpeed(speed: Float) {
        savedSpeeds += speed
    }
}
