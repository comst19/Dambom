@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.comst19.dambom.core.data.download

import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource

internal class HlsExportMediaSource(
    source: MediaSource,
) : WrappingMediaSource(source) {
    override fun onChildSourceInfoRefreshed(newTimeline: Timeline) {
        refreshSourceInfo(
            object : ForwardingTimeline(newTimeline) {
                override fun getWindow(
                    windowIndex: Int,
                    window: Window,
                    defaultPositionProjectionUs: Long,
                ): Window =
                    super.getWindow(windowIndex, window, defaultPositionProjectionUs).apply {
                        // EXT-X-START is a playback hint. Export the entire VOD window.
                        defaultPositionUs = 0L
                    }
            },
        )
    }
}
