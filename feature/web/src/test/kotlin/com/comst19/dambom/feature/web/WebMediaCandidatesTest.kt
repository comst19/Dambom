package com.comst19.dambom.feature.web

import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaResourceRequest
import com.comst19.dambom.core.domain.model.MediaVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebMediaCandidatesTest {
    @Test
    fun `same URL is probed again only when request context changes`() {
        val media = WebMediaCandidates()
        val request = MediaResourceRequest("https://example.com/stream", mapOf("Cookie" to "first"))
        assertTrue(media.claim(request))
        assertFalse(media.claim(request))
        assertTrue(media.claim(request.copy(headers = mapOf("Cookie" to "second"))))
    }

    @Test
    fun `rescan keeps observed request context and stream metadata`() {
        val media = WebMediaCandidates()
        val observed =
            candidate(0).copy(
                requestHeaders = mapOf("Cookie" to "fixture"),
                mimeType = "application/x-mpegURL",
                thumbnailUrl = "https://example.com/poster.jpg",
                variants = listOf(MediaVariant("https://example.com/master", "application/x-mpegURL", null, "90p")),
            )
        media.merge(listOf(observed))
        media.merge(listOf(candidate(0)))
        assertEquals(observed, media.candidates.single())
    }

    @Test
    fun `candidate limit still allows updating an existing candidate and snapshots remain stable`() {
        val media = WebMediaCandidates()
        media.merge((0 until 100).map(::candidate))
        val snapshot = media.candidates
        media.merge(listOf(candidate(100), candidate(0).copy(title = "Updated")))
        assertEquals(100, media.count)
        assertEquals("Updated", media.candidates.first().title)
        assertEquals("Video 0", snapshot.first().title)
    }
}

private fun candidate(index: Int) =
    MediaCandidate(
        id = index.toString(),
        url = "https://example.com/video-$index",
        title = "Video $index",
        mimeType = null,
        contentLength = null,
    )
