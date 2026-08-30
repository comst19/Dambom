package com.comst19.dambom.feature.library

import android.content.Intent
import androidx.core.content.IntentCompat
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.external.originalLinkIntents
import com.comst19.dambom.feature.library.external.originalLinkShareIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoSourcePresentationTest {
    @Test
    fun `lazy content type separates X and website videos`() {
        assertEquals(VideoSourceKind.X, task("https://x.com/user/status/1").libraryContentType())
        assertEquals(VideoSourceKind.WEBSITE, task("https://example.com/video").libraryContentType())
    }

    @Test
    fun `x and legacy Twitter links are recognized as X posts`() {
        assertEquals(VideoSourceKind.X, videoSourcePresentation("https://x.com/user/status/1").kind)
        assertEquals(VideoSourceKind.X, videoSourcePresentation("https://mobile.twitter.com/user/status/1").kind)
        assertNull(videoSourcePresentation("https://x.com/user/status/1").host)
    }

    @Test
    fun `website source keeps a readable host`() {
        assertEquals(
            VideoSourcePresentation(VideoSourceKind.WEBSITE, "media.example.com"),
            videoSourcePresentation("https://www.media.example.com/watch/1"),
        )
    }

    @Test
    fun `X source launches the installed app before browser fallback`() {
        val intents = originalLinkIntents("https://x.com/user/status/1")

        assertEquals("com.twitter.android", intents.first().`package`)
        assertEquals(null, intents.last().`package`)
    }

    @Test
    fun `original link share uses a distinct text sharesheet`() {
        val chooser = originalLinkShareIntent("https://x.com/user/status/1", "Share original link")
        val sendIntent = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Intent.ACTION_SEND, sendIntent?.action)
        assertEquals("text/plain", sendIntent?.type)
        assertEquals("https://x.com/user/status/1", sendIntent?.getStringExtra(Intent.EXTRA_TEXT))
    }
}

private fun task(sourcePageUrl: String) =
    DownloadTask(
        id = sourcePageUrl,
        url = "https://media.example.com/video.mp4",
        sourcePageUrl = sourcePageUrl,
        title = "video",
        mimeType = "video/mp4",
        expectedBytes = 100L,
        downloadedBytes = 100L,
        quality = "원본",
        status = DownloadStatus.COMPLETED,
        failureReason = null,
        localFileName = "video.mp4",
        localFilePath = "/video.mp4",
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
