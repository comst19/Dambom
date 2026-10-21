@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.comst19.dambom.core.data.download

import android.system.ErrnoException
import android.system.OsConstants
import androidx.media3.common.ParserException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.offline.DownloadHelper
import com.comst19.dambom.core.domain.model.DownloadFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreamDownloadEngineTest {
    @Test
    fun `stream type comes from MIME for signed extensionless URLs`() {
        assertEquals(
            "application/x-mpegURL",
            streamMimeType("https://example.test/signed?token=value", "application/vnd.apple.mpegurl"),
        )
        assertEquals(
            "application/dash+xml",
            streamMimeType("https://example.test/signed", "application/dash+xml; charset=UTF-8"),
        )
    }

    @Test
    fun `stream suffix ignores query and fragment`() {
        assertEquals("application/x-mpegURL", streamMimeType("https://example.test/VIDEO.M3U8?token=value#x", null))
        assertEquals(
            "application/dash+xml",
            streamMimeType("https://example.test/video.mpd#time", "application/octet-stream"),
        )
        assertNull(streamMimeType("https://example.test/video.mp4?next=.m3u8", "video/mp4"))
    }

    @Test
    fun `live input is unsupported instead of retried as network failure`() {
        val failure = classifyStreamFailure(DownloadHelper.LiveContentUnsupportedException()) as StreamDownloadException
        assertEquals(DownloadFailureReason.UNSUPPORTED_FORMAT, failure.reason)
    }

    @Test
    fun `malformed manifest is unsupported instead of retried as network failure`() {
        val parserFailure = ParserException.createForMalformedManifest("invalid", null)
        val failure = classifyStreamFailure(parserFailure) as StreamDownloadException
        assertEquals(DownloadFailureReason.UNSUPPORTED_FORMAT, failure.reason)
    }

    @Test
    fun `ordinary transport IO remains retryable`() {
        val failure = IOException("interrupted transport")
        assertSame(failure, classifyStreamFailure(failure))
    }

    @Test
    fun `disk full while muxing is reported as storage exhaustion`() {
        val failure =
            classifyStreamFailure(IOException(ErrnoException("write", OsConstants.ENOSPC))) as StreamDownloadException
        assertEquals(DownloadFailureReason.INSUFFICIENT_STORAGE, failure.reason)
    }

    @Test
    fun `explicit storage failure retains its reason through wrapping`() {
        val expected = StreamDownloadException(DownloadFailureReason.INSUFFICIENT_STORAGE)
        assertSame(expected, classifyStreamFailure(IOException(expected)))
    }

    @Test
    fun `forbidden segment response remains access restricted through wrapping`() {
        val response =
            HttpDataSource.InvalidResponseCodeException(
                403,
                "Forbidden",
                null,
                emptyMap(),
                DataSpec.Builder().setUri("https://example.test/segment.ts").build(),
                byteArrayOf(),
            )
        val failure = classifyStreamFailure(IOException(response)) as StreamDownloadException
        assertEquals(DownloadFailureReason.ACCESS_RESTRICTED, failure.reason)
    }
}
