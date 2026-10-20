package com.comst19.dambom.feature.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.coroutine.ApplicationScope
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.repository.PlaybackHistoryRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.feature.library.player.PlaybackHistoryRecorder
import com.comst19.dambom.feature.library.player.resumePosition
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.io.File
import javax.inject.Inject

@HiltViewModel
internal class VideoPlayerViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val historyRepository: PlaybackHistoryRepository,
        private val settingsRepository: SettingsRepository,
        @ApplicationScope private val applicationScope: CoroutineScope,
        private val appEventBus: AppEventBus,
    ) : ViewModel() {
        private val visibilityState = PlaybackVisibilityState()
        private var preparation: Job? = null
        private var requestedId: String? = null
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

        val player: ExoPlayer =
            ExoPlayer
                .Builder(context)
                .setSeekBackIncrementMs(SEEK_INCREMENT_MILLIS)
                .setSeekForwardIncrementMs(SEEK_INCREMENT_MILLIS)
                .build()
                .apply {
                    setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .setUsage(C.USAGE_MEDIA)
                            .build(),
                        true,
                    )
                    setHandleAudioBecomingNoisy(true)
                }

        init {
            player.addListener(
                object : Player.Listener {
                    override fun onPlayWhenReadyChanged(
                        playWhenReady: Boolean,
                        reason: Int,
                    ) {
                        if (playWhenReady && preparation?.isActive == true && player.currentMediaItem == null) {
                            playRequestedWhilePreparing = true
                            player.pause()
                            return
                        }
                        val candidate = resumeCandidate
                        if (playWhenReady && candidate != null) {
                            player.pause()
                            _resumePromptPosition.value = candidate
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        progressJob?.cancel()
                        if (isPlaying && player.isPlaying && resumeCandidate == null) {
                            history.onPlaying(player)
                            progressJob =
                                viewModelScope.launch {
                                    while (true) {
                                        delay(HISTORY_SAVE_INTERVAL_MILLIS)
                                        history.save(player)
                                    }
                                }
                        } else {
                            history.save(player)
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) history.save(player)
                    }

                    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                        if (applyingSavedSpeed) return
                        speedSelected = true
                        applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            speedWrites.withLock {
                                suspendRunCatching { settingsRepository.setPlaybackSpeed(playbackParameters.speed) }
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
                        player.setPlaybackSpeed(speed)
                        applyingSavedSpeed = false
                    }
                }
            }
        }

        fun prepare(task: DownloadTask) {
            val path = task.localFilePath ?: return
            val uri = Uri.fromFile(File(path))
            if (!shouldReplaceMediaItem(
                    player.currentMediaItem?.mediaId,
                    player.currentMediaItem
                        ?.localConfiguration
                        ?.uri
                        ?.toString(),
                    task.id,
                    uri.toString(),
                ) || (requestedId == task.id && preparation?.isActive == true)
            ) {
                return
            }
            preparation?.cancel()
            requestedId = task.id
            playRequestedWhilePreparing = false
            resumeCandidate = null
            _resumePromptPosition.value = null
            history.save(player)
            history.reset()
            visibilityState.onPlaybackStopped()
            player.pause()
            player.clearMediaItems()
            preparation =
                viewModelScope.launch {
                    val saved =
                        suspendRunCatching {
                            history.awaitPendingWrites()
                            historyRepository.getHistory(task.id)
                        }.onFailure {
                            appEventBus.send(AppEvent.ShowSnackbar(UiText.Resource(R.string.player_history_failure)))
                        }.getOrNull()
                    player.pause()
                    resumeCandidate = resumePosition(saved).takeIf { it > 0L }
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    player.setMediaItem(
                        MediaItem
                            .Builder()
                            .setMediaId(task.id)
                            .setUri(uri)
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(task.title).build())
                            .build(),
                        0L,
                    )
                    player.prepare()
                    if (playRequestedWhilePreparing) {
                        playRequestedWhilePreparing = false
                        requestPlayPause()
                    }
                }
        }

        fun requestPlayPause() {
            if (preparation?.isActive == true && player.currentMediaItem == null) {
                playRequestedWhilePreparing = true
                return
            }
            if (player.playWhenReady) {
                player.pause()
            } else {
                val candidate = resumeCandidate
                if (candidate != null) {
                    _resumePromptPosition.value = candidate
                } else {
                    if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                    player.play()
                }
            }
        }

        fun answerResume(continueFromPosition: Boolean) {
            val position = _resumePromptPosition.value ?: return
            resumeCandidate = null
            _resumePromptPosition.value = null
            player.seekTo(if (continueFromPosition) position else 0L)
            player.play()
        }

        fun dismissResumePrompt() {
            _resumePromptPosition.value = null
        }

        fun onUiStarted() = applyPlaybackCommand(visibilityState.onStarted())

        fun onUiResumed() = applyPlaybackCommand(visibilityState.onResumed())

        fun onUiPaused() = applyPlaybackCommand(visibilityState.onPaused())

        fun onUiStopped() {
            playRequestedWhilePreparing = false
            history.save(player)
            applyPlaybackCommand(visibilityState.onStopped(player.playWhenReady))
        }

        fun onPictureInPictureEntered() = applyPlaybackCommand(visibilityState.onPictureInPictureEntered())

        fun onPictureInPictureExited() = applyPlaybackCommand(visibilityState.onPictureInPictureExited())

        fun stop(id: String) {
            if (player.currentMediaItem?.mediaId != id && requestedId != id) return
            preparation?.cancel()
            requestedId = null
            playRequestedWhilePreparing = false
            _resumePromptPosition.value = null
            resumeCandidate = null
            history.save(player)
            history.reset()
            visibilityState.onPlaybackStopped()
            player.stop()
            player.clearMediaItems()
        }

        fun stopUnavailableVideo() {
            preparation?.cancel()
            requestedId = null
            playRequestedWhilePreparing = false
            _resumePromptPosition.value = null
            resumeCandidate = null
            history.save(player)
            history.reset()
            visibilityState.onPlaybackStopped()
            player.stop()
            player.clearMediaItems()
        }

        override fun onCleared() {
            preparation?.cancel()
            requestedId = null
            playRequestedWhilePreparing = false
            _resumePromptPosition.value = null
            resumeCandidate = null
            history.save(player)
            history.reset()
            applyPlaybackCommand(visibilityState.onDisposed())
            player.release()
        }

        private fun applyPlaybackCommand(command: PlaybackCommand) {
            when (command) {
                PlaybackCommand.Pause -> player.pause()
                PlaybackCommand.Resume -> player.play()
                PlaybackCommand.None -> Unit
            }
        }
    }

private const val SEEK_INCREMENT_MILLIS = 10_000L

internal enum class PlaybackCommand {
    Pause,
    Resume,
    None,
}

internal fun shouldReplaceMediaItem(
    currentMediaId: String?,
    currentUri: String?,
    targetMediaId: String,
    targetUri: String,
): Boolean = currentMediaId != targetMediaId || currentUri != targetUri

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
