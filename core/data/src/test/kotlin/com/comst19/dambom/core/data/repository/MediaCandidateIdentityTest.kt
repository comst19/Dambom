package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.data.mapper.toDomain
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkResult
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkVariant
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkVideo
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCandidateIdentityTest {
    @Test
    fun `direct and X candidates retain source URL hashes including unicode query and fragment`() {
        val expectedIds =
            mapOf(
                "https://example.com/video.mp4" to "da09b2ab4d33db12c2f8ecb9449572e139891ed2b22091aecc863bc85e2dc936",
                "https://example.com/video.mp4?token=a%2Bb#player" to
                    "64c9f9e5473f7faaef1e6d1a4702f2d8fb114827702879ed2897ed0a40f6d9dc",
                "https://example.com/여행.mp4" to "07e46f36e30f0b1e1a711c3b32cae48bff4c63c1daca7c9e9d087c1626775c46",
            )
        for ((sourceUrl, expectedId) in expectedIds) {
            assertEquals(expectedId, sourceUrl.toCandidate("Video", "video/mp4", null).id)
            val result =
                FxTwitterNetworkResult
                    .Success(
                        pageTitle = "Page",
                        videos =
                            listOf(
                                FxTwitterNetworkVideo(
                                    sourceUrl = sourceUrl,
                                    title = "Video",
                                    thumbnailUrl = null,
                                    variants =
                                        listOf(
                                            FxTwitterNetworkVariant(
                                                url = "https://cdn.example.com/720.mp4",
                                                width = 1280,
                                                height = 720,
                                                bitrate = null,
                                                size = null,
                                            ),
                                            FxTwitterNetworkVariant(
                                                url = "https://cdn.example.com/360.mp4",
                                                width = 640,
                                                height = 360,
                                                bitrate = null,
                                                size = null,
                                            ),
                                        ),
                                ),
                            ),
                    ).toDomain() as MediaDetectionResult.Success
            assertEquals(expectedId, result.candidates.single().id)
        }
    }
}
