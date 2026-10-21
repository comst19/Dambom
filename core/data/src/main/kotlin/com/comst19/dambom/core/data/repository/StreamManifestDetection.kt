package com.comst19.dambom.core.data.repository

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.MediaVariant
import com.comst19.dambom.core.domain.model.UnsupportedReason
import okhttp3.ResponseBody
import java.io.ByteArrayInputStream
import java.io.IOException

internal fun streamManifestMime(
    contentType: String?,
    prefix: ByteArray,
): String? {
    val mime = contentType?.substringBefore(';')?.lowercase()
    val text = prefix.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t')
    return when {
        text.startsWith("#EXTM3U") -> HLS_MIME
        text.startsWith("<") && Regex("<(?:[A-Za-z0-9_]+:)?MPD(?:\\s|>)").containsMatchIn(text) -> DASH_MIME
        mime in HLS_CONTENT_TYPES -> HLS_MIME
        mime == DASH_MIME -> DASH_MIME
        else -> null
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun detectStreamManifest(
    url: String,
    mime: String,
    body: ResponseBody,
): MediaDetectionResult {
    val text = body.readUtf8UpTo(MAX_MANIFEST_BYTES) ?: return unsupportedStream()
    return try {
        val heights =
            when (mime) {
                HLS_MIME -> hlsHeights(url, text)
                DASH_MIME -> dashHeights(url, text)
                else -> null
            }
        if (heights == null) {
            unsupportedStream()
        } else {
            val variants =
                heights.distinct().sortedDescending().map { height ->
                    MediaVariant(url, mime, null, "${height}p", videoHeight = height)
                }
            val candidate = url.toCandidate(url.fileTitle(), mime, null).copy(variants = variants)
            MediaDetectionResult.Success(url.fileTitle(), listOf(candidate))
        }
    } catch (_: IOException) {
        unsupportedStream()
    } catch (_: IllegalArgumentException) {
        unsupportedStream()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun hlsHeights(
    url: String,
    text: String,
): List<Int>? =
    when (val playlist = HlsPlaylistParser().parse(Uri.parse(url), ByteArrayInputStream(text.toByteArray()))) {
        is HlsMediaPlaylist -> {
            emptyList<Int>().takeIf { playlist.hasEndTag && playlist.protectionSchemes == null }
        }

        is HlsMultivariantPlaylist -> {
            playlist.variants.map { it.format.height }.filter { it > 0 }.takeIf {
                playlist.variants.isNotEmpty() && playlist.sessionKeyDrmInitData.isEmpty()
            }
        }

        else -> {
            null
        }
    }

@androidx.annotation.OptIn(UnstableApi::class)
private fun dashHeights(
    url: String,
    text: String,
): List<Int>? {
    val manifest = DashManifestParser().parse(Uri.parse(url), ByteArrayInputStream(text.toByteArray()))
    val representations =
        (0 until manifest.periodCount).flatMap { period ->
            manifest.getPeriod(period).adaptationSets.flatMap { it.representations }
        }
    return representations.map { it.format.height }.filter { it > 0 }.takeIf {
        !manifest.dynamic && representations.none { it.format.drmInitData != null }
    }
}

private fun unsupportedStream() = MediaDetectionResult.Unsupported(UnsupportedReason.UNSUPPORTED_FORMAT)

private const val MAX_MANIFEST_BYTES = 2L * 1024L * 1024L
private const val HLS_MIME = "application/x-mpegURL"
private const val DASH_MIME = "application/dash+xml"

private val HLS_CONTENT_TYPES =
    setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl")
