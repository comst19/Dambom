package com.comst19.dambom.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.coroutine.ApplicationScope
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import com.comst19.dambom.core.domain.repository.PlaybackHistoryRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.feature.library.player.PlaybackHistoryRecorder
import com.comst19.dambom.feature.library.player.VideoPlaybackController
import com.comst19.dambom.feature.library.player.VideoPlaybackListener
import com.comst19.dambom.feature.library.player.resumePosition
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel(assistedFactory = VideoPlayerViewModel.Factory::class)
internal class VideoPlayerViewModel
    @AssistedInject
    constructor(
        @Assisted private val playback: VideoPlaybackController,
        private val historyRepository: PlaybackHistoryRepository,
        private val settingsRepository: SettingsRepository,
        @ApplicationScope private val applicationScope: CoroutineScope,
        private val appEventBus: AppEventBus,
        private val privateVideoSession: PrivateVideoSession,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(playback: VideoPlaybackController): VideoPlayerViewModel
        }

        private val visibilityState = PlaybackVisibilityState()
        private var preparation: Job? = null
        private var requestedId: String? = null
        private var requestedPath: String? = null
        private var privatePlayback = false
        private var playRequestedWhilePreparing = false
        private var progressJob: Job? = null
        private val speedWrites = Mutex()
        private var applyingSavedSpeed = false
        private var speedSelected = false
        private var resumeCandidate: Long? = null
        private val _resumePromptPosition = MutableStateFlow<Long?>(null)
        val resumePromptPosition = _resumePromptPosition.asStateFlow()
        private val history =
            PlaybackHistoryRecorder(historyRepository, applicationScope) {
                appEventBus.send(AppEvent.ShowSnackbar(UiText.Resource(R.string.player_history_failure)))
            }

        init {
            viewModelScope.launch {
                privateVideoSession.isUnlocked.collect { unlocked ->
                    if (!unlocked && privatePlayback) stopUnavailableVideo()
                }
            }
            playback.setListener(
                object : VideoPlaybackListener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
                        if (playWhenReady && preparation?.isActive == true && playback.currentMediaId == null) {
                            playRequestedWhilePreparing = true
                            playback.pause()
                            return
                        }
                        val candidate = resumeCandidate
                        if (playWhenReady && candidate != null) {
                            playback.pause()
                            _resumePromptPosition.value = candidate
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        progressJob?.cancel()
                        if (isPlaying && playback.isPlaying && resumeCandidate == null) {
                            history.onPlaying(playback.currentProgress)
                            progressJob =
                                viewModelScope.launch {
                                    while (true) {
                                        delay(HISTORY_SAVE_INTERVAL_MILLIS)
                                        history.save(playback.currentProgress)
                                    }
                                }
                        } else {
                            history.save(playback.currentProgress)
                        }
                    }

                    override fun onPlaybackEnded() {
                        history.save(playback.currentProgress)
                    }

                    override fun onPlaybackSpeedChanged(speed: Float) {
                        if (applyingSavedSpeed) return
                        speedSelected = true
                        applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            speedWrites.withLock {
                                suspendRunCatching { settingsRepository.setPlaybackSpeed(speed) }
                                    .onFailure {
                                        val message = UiText.Resource(R.string.player_settings_failure)
                                        appEventBus.send(AppEvent.ShowSnackbar(message))
                                    }
                            }
                        }
                    }
                },
            )
            viewModelScope.launch {
                settingsRepository.settings.map { it.playbackSpeed }.distinctUntilChanged().collect { speed ->
                    if (!speedSelected) {
                        applyingSavedSpeed = true
                        playback.setPlaybackSpeed(speed)
                        applyingSavedSpeed = false
                    }
                }
            }
        }

        @Suppress("ReturnCount")
        fun prepare(task: DownloadTask) {
            if (task.isPrivate && !privateVideoSession.isUnlocked.value) {
                stopUnavailableVideo()
                return
            }
            privatePlayback = task.isPrivate
            val path = task.localFilePath ?: return
            if (isAlreadyPreparedOrPending(task.id, path)) {
                return
            }
            preparation?.cancel()
            requestedId = task.id
            requestedPath = path
            playRequestedWhilePreparing = false
            resumeCandidate = null
            _resumePromptPosition.value = null
            history.save(playback.currentProgress)
            history.reset()
            visibilityState.onPlaybackStopped()
            playback.pause()
            playback.clearMediaItems()
            preparation =
                viewModelScope.launch {
                    val saved =
                        suspendRunCatching {
                            history.awaitPendingWrites()
                            historyRepository.getHistory(task.id)
                        }.onFailure {
                            appEventBus.send(AppEvent.ShowSnackbar(UiText.Resource(R.string.player_history_failure)))
                        }.getOrNull()
                    playback.pause()
                    resumeCandidate = resumePosition(saved).takeIf { it > 0L }
                    playback.prepare(mediaId = task.id, localFilePath = path, title = task.title)
                    if (playRequestedWhilePreparing) {
                        playRequestedWhilePreparing = false
                        requestPlayPause()
                    }
                }
        }

        fun requestPlayPause() {
            if (privatePlayback && !privateVideoSession.isUnlocked.value) {
                stopUnavailableVideo()
                return
            }
            if (preparation?.isActive == true && playback.currentMediaId == null) {
                playRequestedWhilePreparing = true
                return
            }
            if (playback.playWhenReady) {
                playback.pause()
            } else {
                val candidate = resumeCandidate
                if (candidate != null) {
                    _resumePromptPosition.value = candidate
                } else {
                    if (playback.isEnded) playback.seekToDefaultPosition()
                    playback.play()
                }
            }
        }

        fun answerResume(continueFromPosition: Boolean) {
            val position = _resumePromptPosition.value ?: return
            resumeCandidate = null
            _resumePromptPosition.value = null
            playback.seekTo(if (continueFromPosition) position else 0L)
            playback.play()
        }

        fun dismissResumePrompt() {
            _resumePromptPosition.value = null
        }

        fun onUiStarted() = applyPlaybackCommand(visibilityState.onStarted())

        fun onUiResumed() = applyPlaybackCommand(visibilityState.onResumed())

        fun onUiPaused() = applyPlaybackCommand(visibilityState.onPaused())

        fun onUiStopped() {
            playRequestedWhilePreparing = false
            history.save(playback.currentProgress)
            applyPlaybackCommand(visibilityState.onStopped(playback.playWhenReady))
        }

        fun onPictureInPictureEntered() = applyPlaybackCommand(visibilityState.onPictureInPictureEntered())

        fun onPictureInPictureExited() = applyPlaybackCommand(visibilityState.onPictureInPictureExited())

        fun stop(id: String) {
            if (playback.currentMediaId != id && requestedId != id) return
            stopUnavailableVideo()
        }

        fun stopUnavailableVideo() {
            preparation?.cancel()
            requestedId = null
            requestedPath = null
            playRequestedWhilePreparing = false
            _resumePromptPosition.value = null
            resumeCandidate = null
            history.save(playback.currentProgress)
            history.reset()
            visibilityState.onPlaybackStopped()
            playback.stopAndClear()
        }

        override fun onCleared() {
            preparation?.cancel()
            requestedId = null
            requestedPath = null
            playRequestedWhilePreparing = false
            _resumePromptPosition.value = null
            resumeCandidate = null
            history.save(playback.currentProgress)
            history.reset()
            applyPlaybackCommand(visibilityState.onDisposed())
            playback.release()
        }

        private fun isAlreadyPreparedOrPending(
            taskId: String,
            localFilePath: String,
        ): Boolean {
            val samePendingRequest =
                requestedId == taskId &&
                    requestedPath == localFilePath &&
                    preparation?.isActive == true
            return !shouldReplaceMediaItem(
                playback.currentMediaId,
                playback.currentSourcePath,
                taskId,
                localFilePath,
            ) || samePendingRequest
        }

        private fun applyPlaybackCommand(command: PlaybackCommand) {
            when (command) {
                PlaybackCommand.Pause -> playback.pause()
                PlaybackCommand.Resume -> playback.play()
                PlaybackCommand.None -> Unit
            }
        }
    }

internal enum class PlaybackCommand {
    Pause,
    Resume,
    None,
}

internal fun shouldReplaceMediaItem(
    currentMediaId: String?,
    currentSourcePath: String?,
    targetMediaId: String,
    targetSourcePath: String,
): Boolean = currentMediaId != targetMediaId || currentSourcePath != targetSourcePath

internal class PlaybackVisibilityState {
    private var resumeRequested = false
    private var isInPictureInPicture = false
    private var stoppedWhileInPictureInPicture = false
    private var isResolvingPictureInPictureExit = false

    fun onStarted(): PlaybackCommand =
        if (resumeRequested) {
            resumeRequested = false
            PlaybackCommand.Resume
        } else {
            PlaybackCommand.None
        }

    fun onResumed(): PlaybackCommand {
        if (isResolvingPictureInPictureExit) {
            isResolvingPictureInPictureExit = false
        }
        return PlaybackCommand.None
    }

    fun onPaused(): PlaybackCommand = PlaybackCommand.None

    fun onStopped(wasPlayWhenReady: Boolean): PlaybackCommand {
        if (isInPictureInPicture) {
            stoppedWhileInPictureInPicture = true
            return PlaybackCommand.None
        }
        resumeRequested = if (isResolvingPictureInPictureExit) false else wasPlayWhenReady
        isResolvingPictureInPictureExit = false
        return PlaybackCommand.Pause
    }

    fun onPictureInPictureEntered(): PlaybackCommand {
        isInPictureInPicture = true
        stoppedWhileInPictureInPicture = false
        return PlaybackCommand.None
    }

    fun onPictureInPictureExited(): PlaybackCommand {
        if (isInPictureInPicture) {
            isInPictureInPicture = false
            if (stoppedWhileInPictureInPicture) {
                onPlaybackStopped()
                return PlaybackCommand.Pause
            }
            isResolvingPictureInPictureExit = true
        }

        return PlaybackCommand.None
    }

    fun onPlaybackStopped() {
        resumeRequested = false
        isInPictureInPicture = false
        stoppedWhileInPictureInPicture = false
        isResolvingPictureInPictureExit = false
    }

    fun onDisposed(): PlaybackCommand {
        onPlaybackStopped()
        return PlaybackCommand.Pause
    }
}

private const val HISTORY_SAVE_INTERVAL_MILLIS = 5_000L
