package com.comst19.dambom.core.data.repository

import android.net.Uri
import android.util.Xml
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.MediaVariant
import com.comst19.dambom.core.domain.model.UnsupportedReason
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.StringReader

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

internal data class StreamManifestInspection(
    val result: MediaDetectionResult,
    val childPlaylistUrls: List<String> = emptyList(),
)

internal fun detectStreamManifest(
    url: String,
    mime: String,
    body: ResponseBody,
): MediaDetectionResult = inspectStreamManifest(url, mime, body).result

@androidx.annotation.OptIn(UnstableApi::class)
internal fun inspectStreamManifest(
    url: String,
    mime: String,
    body: ResponseBody,
): StreamManifestInspection {
    val text = body.readUtf8UpTo(MAX_MANIFEST_BYTES) ?: return unsupportedStream()
    return try {
        val tracks =
            when (mime) {
                HLS_MIME -> {
                    if (text.hasProtectedHlsKeys()) {
                        ManifestTracks(
                            reason = UnsupportedReason.DRM_PROTECTED,
                        )
                    } else {
                        inspectHls(url, text)
                    }
                }

                DASH_MIME -> {
                    inspectDash(url, text)
                }

                else -> {
                    ManifestTracks(reason = UnsupportedReason.UNSUPPORTED_FORMAT)
                }
            }
        val reason = tracks.reason
        if (reason != null) {
            unsupportedStream(reason)
        } else {
            val variants =
                tracks.heights.distinct().sortedDescending().map { height ->
                    MediaVariant(url, mime, null, "${height}p", videoHeight = height)
                }
            val candidate = url.toCandidate(url.fileTitle(), mime, null).copy(variants = variants)
            StreamManifestInspection(
                MediaDetectionResult.Success(url.fileTitle(), listOf(candidate)),
                tracks.childPlaylistUrls,
            )
        }
    } catch (_: IOException) {
        unsupportedStream()
    } catch (_: IllegalArgumentException) {
        unsupportedStream()
    } catch (_: XmlPullParserException) {
        unsupportedStream()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun inspectHls(
    url: String,
    text: String,
): ManifestTracks =
    when (val playlist = HlsPlaylistParser().parse(Uri.parse(url), ByteArrayInputStream(text.toByteArray()))) {
        is HlsMediaPlaylist -> {
            ManifestTracks(
                reason =
                    when {
                        playlist.protectionSchemes != null -> UnsupportedReason.DRM_PROTECTED
                        !playlist.hasEndTag -> UnsupportedReason.UNSUPPORTED_FORMAT
                        else -> null
                    },
            )
        }

        is HlsMultivariantPlaylist -> {
            ManifestTracks(
                heights = playlist.variants.map { it.format.height }.filter { it > 0 },
                childPlaylistUrls =
                    (playlist.variants.map { it.url } + playlist.audios.mapNotNull { it.url })
                        .map(Uri::toString)
                        .distinct(),
                reason =
                    when {
                        playlist.sessionKeyDrmInitData.isNotEmpty() -> UnsupportedReason.DRM_PROTECTED
                        playlist.variants.isEmpty() -> UnsupportedReason.UNSUPPORTED_FORMAT
                        else -> null
                    },
            )
        }

        else -> {
            ManifestTracks(reason = UnsupportedReason.UNSUPPORTED_FORMAT)
        }
    }

@androidx.annotation.OptIn(UnstableApi::class)
private fun inspectDash(
    url: String,
    text: String,
): ManifestTracks {
    val manifest = DashManifestParser().parse(Uri.parse(url), ByteArrayInputStream(text.toByteArray()))
    val representations =
        (0 until manifest.periodCount).flatMap { period ->
            manifest.getPeriod(period).adaptationSets.flatMap { it.representations }
        }
    return ManifestTracks(
        heights = representations.map { it.format.height }.filter { it > 0 },
        reason =
            when {
                representations.any { it.format.drmInitData != null } || text.hasDashContentProtection() -> {
                    UnsupportedReason.DRM_PROTECTED
                }

                manifest.dynamic -> {
                    UnsupportedReason.UNSUPPORTED_FORMAT
                }

                else -> {
                    null
                }
            },
    )
}

private data class ManifestTracks(
    val heights: List<Int> = emptyList(),
    val childPlaylistUrls: List<String> = emptyList(),
    val reason: UnsupportedReason? = null,
)

private fun unsupportedStream(reason: UnsupportedReason = UnsupportedReason.UNSUPPORTED_FORMAT) =
    StreamManifestInspection(MediaDetectionResult.Unsupported(reason))

private const val MAX_MANIFEST_BYTES = 2L * 1024L * 1024L
private const val HLS_MIME = "application/x-mpegURL"
private const val DASH_MIME = "application/dash+xml"

private val HLS_CONTENT_TYPES =
    setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl")

private fun String.hasProtectedHlsKeys(): Boolean =
    lineSequence().filter { it.startsWith("#EXT-X-KEY:") || it.startsWith("#EXT-X-SESSION-KEY:") }.any { line ->
        val attributes =
            KEY_ATTRIBUTE_REGEX.findAll(line.substringAfter(':')).associate { match ->
                val (name, quotedValue, unquotedValue) = match.destructured
                name to quotedValue.ifEmpty { unquotedValue }
            }
        val method = attributes["METHOD"]
        val format = attributes["KEYFORMAT"]
        method != null && method != "NONE" && (method != "AES-128" || (format != null && format != "identity"))
    }

private val KEY_ATTRIBUTE_REGEX = Regex("""(?:^|,)([A-Z0-9-]+)=(?:"([^"]*)"|([^,]*))""")

private fun String.hasDashContentProtection(): Boolean {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    parser.setInput(StringReader(this))
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "ContentProtection") return true
        parser.next()
    }
    return false
}
