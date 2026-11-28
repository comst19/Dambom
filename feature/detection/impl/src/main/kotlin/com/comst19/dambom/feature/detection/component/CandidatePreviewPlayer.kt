package com.comst19.dambom.feature.detection.component

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.comst19.dambom.core.common.ui.player.MediaPreviewDataSourceFactory
import com.comst19.dambom.core.domain.model.MediaCandidate

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createCandidatePreviewPlayer(
    context: Context,
    candidate: MediaCandidate? = null,
    previewDataSourceFactory: MediaPreviewDataSourceFactory? = null,
): ExoPlayer =
    ExoPlayer
        .Builder(context)
        .apply {
            if (candidate != null) {
                previewDataSourceFactory?.let { factory ->
                    setMediaSourceFactory(
                        DefaultMediaSourceFactory(factory.create(candidate.url, candidate.requestHeaders)),
                    )
                }
                candidate.videoHeight?.let { height ->
                    val selector = DefaultTrackSelector(context)
                    selector.setParameters(
                        selector
                            .buildUponParameters()
                            .setMaxVideoSize(Int.MAX_VALUE, height)
                            .setExceedVideoConstraintsIfNecessary(false),
                    )
                    setTrackSelector(selector)
                }
            }
        }.setSeekBackIncrementMs(PREVIEW_SEEK_INCREMENT_MILLIS)
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
