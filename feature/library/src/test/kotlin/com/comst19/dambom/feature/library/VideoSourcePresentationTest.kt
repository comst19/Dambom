package com.comst19.dambom.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoSourcePresentationTest {
    @Test
    fun `x and legacy Twitter links are recognized as X posts`() {
        assertEquals(VideoSourceKind.X, videoSourcePresentation("https://x.com/user/status/1").kind)
        assertEquals(VideoSourceKind.X, videoSourcePresentation("https://mobile.twitter.com/user/status/1").kind)
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
}
