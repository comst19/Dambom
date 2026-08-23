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

        fun onUiPaused() {
            visibilityState.onHidden(player.playWhenReady)
            player.pause()
        }

        fun onUiResumed() {
            if (visibilityState.consumeResumeRequest()) player.play()
        }

        override fun onCleared() {
            player.release()
        }
    }

internal class PlaybackVisibilityState {
    private var resumeRequested = false

    fun onHidden(wasPlayWhenReady: Boolean) {
        resumeRequested = wasPlayWhenReady
    }

    fun consumeResumeRequest(): Boolean =
        resumeRequested.also {
            resumeRequested = false
        }
}
