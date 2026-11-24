package com.comst19.dambom.feature.detection.component

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.network.header.mediaRequestClient
import okhttp3.OkHttpClient

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createCandidatePreviewPlayer(
    context: Context,
    candidate: MediaCandidate? = null,
): ExoPlayer =
    ExoPlayer
        .Builder(context)
        .apply {
            if (candidate != null) {
                val client = mediaRequestClient(previewHttpClient, candidate.url, candidate.requestHeaders)
                setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(client)))
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

private val previewHttpClient = OkHttpClient()
