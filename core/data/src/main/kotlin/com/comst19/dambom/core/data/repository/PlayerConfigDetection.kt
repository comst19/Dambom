package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.MediaDetectionResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URI

internal fun parsePlayerConfig(
    requestUrl: String,
    text: String,
): MediaDetectionResult.Success? {
    val uri = URI(requestUrl)
    val metadata =
        when {
            uri.host.equals("player.vimeo.com", true) && VIMEO_PLAYER_PATH.matches(uri.path) -> {
                val json = config(text) ?: embeddedVimeoConfig(text) ?: return null
                parseVimeoConfig(requestUrl, json)
            }

            uri.host.equals("tvid.in", true) && uri.path.startsWith("/api/mediainfo/") -> {
                config(text)?.let { parseTvidConfig(it) }
            }

            else -> {
                null
            }
        }
    return metadata?.toDetectionResult(requestUrl)
}

private fun parseVimeoConfig(
    baseUrl: String,
    config: JsonObject,
): PlayerMediaMetadata {
    val variants =
        config
            .obj("request")
            ?.obj("files")
            ?.let { vimeoVariants(baseUrl, it) }
            .orEmpty()
    val video = config.obj("video")
    return PlayerMediaMetadata(video?.text("title"), video?.obj("thumbs")?.text("base"), variants)
}

private fun vimeoVariants(
    baseUrl: String,
    files: JsonObject,
): List<PlayerMediaSource> {
    val adaptive =
        listOf("hls" to HLS_CONFIG_MIME, "dash" to "application/dash+xml").mapNotNull { (key, mime) ->
            val stream = files.obj(key) ?: return@mapNotNull null
            val cdns = stream.obj("cdns") ?: return@mapNotNull null
            val preferred = stream.text("default_cdn")?.let { cdns.obj(it) }
            val url =
                (listOfNotNull(preferred) + cdns.values.filterIsInstance<JsonObject>())
                    .firstNotNullOfOrNull { resolvePlayerMediaUrl(baseUrl, it.text("url")) } ?: return@mapNotNull null
            PlayerMediaSource(url, mime)
        }
    val progressive =
        (files["progressive"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { item ->
            val url = resolvePlayerMediaUrl(baseUrl, item.text("url")) ?: return@mapNotNull null
            val height = (item["height"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
            PlayerMediaSource(url, "video/mp4", item.text("quality"), height)
        }
    return (adaptive + progressive).distinctBy { it.url }
}

private fun parseTvidConfig(config: JsonObject): PlayerMediaMetadata {
    val sources =
        (config["flavors"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { flavor ->
            if (flavor.text("type") != "hls") return@mapNotNull null
            val url = flavor.text("url") ?: return@mapNotNull null
            PlayerMediaSource(url, HLS_CONFIG_MIME)
        }
    return PlayerMediaMetadata(config.text("name"), config.text("image"), sources)
}

private fun embeddedVimeoConfig(html: String): JsonObject? {
    var opening = html.indexOf("<script", ignoreCase = true)
    while (opening >= 0) {
        val contentStart = html.indexOf('>', opening)
        val closing = if (contentStart >= 0) html.indexOf("</script>", contentStart + 1, true) else -1
        if (closing < 0) break
        val text = html.substring(contentStart + 1, closing)
        val assignment = VIMEO_CONFIG_ASSIGNMENT.find(text)
        val value = assignment?.let { text.substring(it.range.last + 1).trimStart() }
        val parsed = value?.let(::jsonObjectPrefix)?.let(::config)
        if (parsed != null) return parsed
        opening = html.indexOf("<script", closing + SCRIPT_CLOSE_TAG_LENGTH, true)
    }
    return null
}

private fun jsonObjectPrefix(text: String): String? {
    if (!text.startsWith('{')) return null
    var depth = 0
    var quoted = false
    var escaped = false
    var end: Int? = null
    for ((index, char) in text.withIndex()) {
        when {
            escaped -> escaped = false
            quoted && char == '\\' -> escaped = true
            char == '"' -> quoted = !quoted
            !quoted && char == '{' -> depth++
            !quoted && char == '}' -> depth--
        }
        if (depth == 0) {
            end = index + 1
            break
        }
    }
    return end?.let { text.substring(0, it) }
}

private fun config(text: String): JsonObject? = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

private const val HLS_CONFIG_MIME = "application/x-mpegURL"
private val VIMEO_PLAYER_PATH = Regex("/video/\\d+(?:/config)?/?")
private val VIMEO_CONFIG_ASSIGNMENT = Regex("\\bwindow\\.playerConfig\\s*=\\s*")
private const val SCRIPT_CLOSE_TAG_LENGTH = 9
