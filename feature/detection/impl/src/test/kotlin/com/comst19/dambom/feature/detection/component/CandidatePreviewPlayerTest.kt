package com.comst19.dambom.feature.detection.component

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.player.MediaPreviewDataSourceFactory
import com.comst19.dambom.core.domain.model.MediaCandidate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidatePreviewPlayerTest {
    @Test
    fun `preview applies the selected adaptive video height`() {
        val candidate =
            MediaCandidate(
                "video",
                "https://example.com/master.m3u8",
                "Video",
                null,
                null,
                videoHeight = 90,
            )
        val player = createCandidatePreviewPlayer(ApplicationProvider.getApplicationContext<Context>(), candidate)
        try {
            assertEquals(90, player.trackSelectionParameters.maxVideoHeight)
        } finally {
            player.release()
        }
    }

    @Test
    fun `preview seeks ten seconds in both directions`() {
        val player = createCandidatePreviewPlayer(ApplicationProvider.getApplicationContext<Context>())

        try {
            assertEquals(
                listOf(10_000L, 10_000L),
                listOf(player.seekBackIncrement, player.seekForwardIncrement),
            )
        } finally {
            player.release()
        }
    }

    @Test
    fun `preview passes candidate url and headers to injected preview data source factory`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorded = mutableListOf<Pair<String, Map<String, String>>>()
        val factory =
            MediaPreviewDataSourceFactory { mediaUrl, requestHeaders ->
                recorded += mediaUrl to requestHeaders
                DefaultDataSource.Factory(context)
            }
        val candidate =
            MediaCandidate(
                id = "video",
                url = "https://cdn.example/video.mp4",
                title = "Video",
                mimeType = "video/mp4",
                contentLength = null,
                requestHeaders = mapOf("user-agent" to "PreviewAgent", "referer" to "https://origin.example/page"),
            )

        val player = createCandidatePreviewPlayer(context, candidate, factory)
        try {
            assertEquals(
                listOf("https://cdn.example/video.mp4" to candidate.requestHeaders),
                recorded,
            )
        } finally {
            player.release()
        }
    }
}
