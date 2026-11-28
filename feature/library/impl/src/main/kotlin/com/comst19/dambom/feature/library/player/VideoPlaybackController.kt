package com.comst19.dambom.feature.library.player

internal data class PlaybackProgressSnapshot(
    val mediaId: String,
    val positionMillis: Long,
    val durationMillis: Long,
)

internal interface VideoPlaybackListener {
    fun onPlayWhenReadyChanged(playWhenReady: Boolean)

    fun onIsPlayingChanged(isPlaying: Boolean)

    fun onPlaybackEnded()

    fun onPlaybackSpeedChanged(speed: Float)
}

internal interface VideoPlaybackController {
    val currentMediaId: String?
    val currentSourcePath: String?
    val playWhenReady: Boolean
    val isPlaying: Boolean
    val isEnded: Boolean
    val currentProgress: PlaybackProgressSnapshot?

    fun setListener(listener: VideoPlaybackListener?)

    fun prepare(
        mediaId: String,
        localFilePath: String,
        title: String,
    )

    fun play()

    fun pause()

    fun seekTo(positionMillis: Long)

    fun seekToDefaultPosition()

    fun setPlaybackSpeed(speed: Float)

    fun clearMediaItems()

    fun stopAndClear()

    fun release()
}
