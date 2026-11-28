package com.comst19.dambom.feature.library.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal class Media3VideoPlaybackController
    internal constructor(
        val player: Player,
    ) : VideoPlaybackController {
        constructor(context: Context) : this(createExoPlayer(context))

        private val released = AtomicBoolean(false)
        private var listener: VideoPlaybackListener? = null

        private val playerListener =
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    listener?.onPlayWhenReadyChanged(playWhenReady)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    listener?.onIsPlayingChanged(isPlaying)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        listener?.onPlaybackEnded()
                    }
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    listener?.onPlaybackSpeedChanged(playbackParameters.speed)
                }
            }

        init {
            player.addListener(playerListener)
        }

        override val currentMediaId: String?
            get() = player.currentMediaItem?.mediaId

        override val currentSourcePath: String?
            get() =
                player.currentMediaItem
                    ?.localConfiguration
                    ?.uri
                    ?.takeIf { it.scheme == FILE_URI_SCHEME }
                    ?.path

        override val playWhenReady: Boolean
            get() = player.playWhenReady

        override val isPlaying: Boolean
            get() = player.isPlaying

        override val isEnded: Boolean
            get() = player.playbackState == Player.STATE_ENDED

        override val currentProgress: PlaybackProgressSnapshot?
            get() {
                val mediaId = player.currentMediaItem?.mediaId ?: return null
                return PlaybackProgressSnapshot(
                    mediaId = mediaId,
                    positionMillis = player.currentPosition,
                    durationMillis = player.duration,
                )
            }

        override fun setListener(listener: VideoPlaybackListener?) {
            this.listener = listener
        }

        override fun prepare(
            mediaId: String,
            localFilePath: String,
            title: String,
        ) {
            val uri = Uri.fromFile(File(localFilePath))
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.setMediaItem(
                MediaItem
                    .Builder()
                    .setMediaId(mediaId)
                    .setUri(uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                    .build(),
                0L,
            )
            player.prepare()
        }

        override fun play() {
            player.play()
        }

        override fun pause() {
            player.pause()
        }

        override fun seekTo(positionMillis: Long) {
            player.seekTo(positionMillis)
        }

        override fun seekToDefaultPosition() {
            player.seekToDefaultPosition()
        }

        override fun setPlaybackSpeed(speed: Float) {
            player.setPlaybackSpeed(speed)
        }

        override fun clearMediaItems() {
            player.clearMediaItems()
        }

        override fun stopAndClear() {
            player.stop()
            player.clearMediaItems()
        }

        override fun release() {
            if (!released.compareAndSet(false, true)) return
            listener = null
            player.removeListener(playerListener)
            player.release()
        }
    }

private fun createExoPlayer(context: Context): ExoPlayer =
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

private const val FILE_URI_SCHEME = "file"
private const val SEEK_INCREMENT_MILLIS = 10_000L
