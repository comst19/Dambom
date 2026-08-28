package com.comst19.dambom.feature.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.comst19.dambom.core.domain.model.DownloadTask
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
internal class VideoPlayerViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ViewModel() {
        private val visibilityState = PlaybackVisibilityState()

        val player: ExoPlayer =
            ExoPlayer
                .Builder(context)
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

        fun play(task: DownloadTask) {
            val path = task.localFilePath ?: return
            if (player.currentMediaItem?.mediaId == task.id) return
            player.setMediaItem(
                MediaItem
                    .Builder()
                    .setMediaId(task.id)
                    .setUri(Uri.fromFile(File(path)))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(task.title).build())
                    .build(),
            )
            player.prepare()
            player.play()
        }

        fun onUiStarted() = applyPlaybackCommand(visibilityState.onStarted())

        fun onUiResumed() = applyPlaybackCommand(visibilityState.onResumed())

        fun onUiPaused() = applyPlaybackCommand(visibilityState.onPaused())

        fun onUiStopped() = applyPlaybackCommand(visibilityState.onStopped(player.playWhenReady))

        fun onPictureInPictureEntered() = applyPlaybackCommand(visibilityState.onPictureInPictureEntered())

        fun onPictureInPictureExited() = applyPlaybackCommand(visibilityState.onPictureInPictureExited())

        fun stop(id: String) {
            if (player.currentMediaItem?.mediaId != id) return
            visibilityState.onPlaybackStopped()
            player.stop()
            player.clearMediaItems()
        }

        override fun onCleared() {
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

internal enum class PlaybackCommand {
    Pause,
    Resume,
    None,
}

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
