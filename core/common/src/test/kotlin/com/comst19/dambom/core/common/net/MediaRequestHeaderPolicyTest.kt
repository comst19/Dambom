package com.comst19.dambom.core.common.net

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaRequestHeaderPolicyTest {
    @Test
    fun `variant own headers take precedence over candidate headers`() {
        val resolved =
            resolveMediaVariantHeaders(
                candidateUrl = "https://origin.example/master.m3u8",
                candidateHeaders =
                    mapOf(
                        "Cookie" to "candidate=1",
                        "User-Agent" to "CandidateAgent",
                    ),
                variantUrl = "https://cdn.example/720.mp4",
                variantHeaders =
                    mapOf(
                        "Cookie" to "variant=2",
                        "Authorization" to "Bearer variant",
                        "Range" to "bytes=0-",
                    ),
            )

        assertEquals(
            mapOf(
                "cookie" to "variant=2",
                "authorization" to "Bearer variant",
            ),
            resolved,
        )
    }

    @Test
    fun `candidate headers keep credentials on same origin including default ports`() {
        val resolved =
            resolveMediaVariantHeaders(
                candidateUrl = "https://ORIGIN.example/master.m3u8?filter=a|b",
                candidateHeaders =
                    mapOf(
                        "Cookie" to "session=abc",
                        "Authorization" to "Bearer token",
                        "User-Agent" to "DambomAgent",
                    ),
                variantUrl = "https://origin.example:443/video.mp4?quality=hd|1",
                variantHeaders = emptyMap(),
            )

        assertEquals(
            mapOf(
                "cookie" to "session=abc",
                "authorization" to "Bearer token",
                "user-agent" to "DambomAgent",
            ),
            resolved,
        )
    }

    @Test
    fun `candidate headers strip credentials across origins while preserving shared headers`() {
        val resolved =
            resolveMediaVariantHeaders(
                candidateUrl = "https://origin.example/master.m3u8",
                candidateHeaders =
                    mapOf(
                        "Cookie" to "session=abc",
                        "Authorization" to "Bearer token",
                        "X-Custom-Secret" to "hidden",
                        "User-Agent" to "DambomAgent",
                        "Referer" to "https://origin.example/watch",
                        "Origin" to "bad\r\nheader",
                    ),
                variantUrl = "https://cdn.other.example/video.mp4",
                variantHeaders = emptyMap(),
            )

        assertEquals(
            mapOf(
                "user-agent" to "DambomAgent",
                "referer" to "https://origin.example/watch",
            ),
            resolved,
        )
    }

    @Test
    fun `different scheme or non default port counts as cross origin`() {
        val headers = mapOf("Cookie" to "session=abc", "User-Agent" to "Agent")

        assertEquals(
            mapOf("user-agent" to "Agent"),
            mediaHeadersForUrl(headers, "https://origin.example/a", "http://origin.example/b"),
        )
        assertEquals(
            mapOf("user-agent" to "Agent"),
            mediaHeadersForUrl(headers, "https://origin.example/a", "https://origin.example:8443/b"),
        )
    }

    @Test
    fun `same origin keeps credentials across ipv6 notations idn punycode and underscore hosts`() {
        val headers =
            mapOf(
                "Cookie" to "session=abc",
                "Authorization" to "Bearer token",
                "User-Agent" to "DambomAgent",
            )
        val expected =
            mapOf(
                "cookie" to "session=abc",
                "authorization" to "Bearer token",
                "user-agent" to "DambomAgent",
            )
        val sameOriginPairs =
            listOf(
                "https://[0:0:0:0:0:0:0:1]/master.m3u8" to "https://[::1]/720.m3u8",
                "https://[2001:0db8:0000:0000::1]/master.m3u8" to "https://[2001:DB8::1]:443/720.m3u8",
                "https://bücher.example/master.m3u8" to "https://xn--bcher-kva.example/720.m3u8",
                "https://video_edge.example/master.m3u8" to "https://video_edge.example/720.m3u8",
            )

        sameOriginPairs.forEach { (source, target) ->
            assertEquals(
                "Expected same origin for $source -> $target",
                expected,
                mediaHeadersForUrl(headers, source, target),
            )
        }
    }
}
