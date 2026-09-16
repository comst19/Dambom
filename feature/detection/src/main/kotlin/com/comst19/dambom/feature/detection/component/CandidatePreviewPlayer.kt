package com.comst19.dambom.feature.detection.component

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

internal fun createCandidatePreviewPlayer(context: Context): ExoPlayer =
    ExoPlayer
        .Builder(context)
        .setSeekBackIncrementMs(PREVIEW_SEEK_INCREMENT_MILLIS)
        .setSeekForwardIncrementMs(PREVIEW_SEEK_INCREMENT_MILLIS)
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

private const val PREVIEW_SEEK_INCREMENT_MILLIS = 10_000L
