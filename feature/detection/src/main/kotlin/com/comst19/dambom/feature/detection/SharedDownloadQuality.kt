package com.comst19.dambom.feature.detection

import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaVariant
import com.comst19.dambom.core.domain.model.SharedLinkQuality

internal fun MediaCandidate.preferredSharedVariant(settings: AppSettings): MediaVariant {
    val ranked =
        downloadVariants.sortedWith(
            compareBy<MediaVariant> { it.resolution ?: -1 }.thenBy { it.bitrate ?: -1 }.thenBy {
                it.contentLength
                    ?: -1
            },
        )
    val known = ranked.filter { it.resolution != null }
    return when (settings.sharedLinkQuality) {
        SharedLinkQuality.HIGHEST -> {
            (known.ifEmpty { ranked }).last()
        }

        SharedLinkQuality.LOWEST -> {
            (known.ifEmpty { ranked }).first()
        }

        SharedLinkQuality.PREFERRED_RESOLUTION -> {
            known.lastOrNull { it.resolution!! <= settings.sharedLinkResolution }
                ?: (known.ifEmpty { ranked }).first()
        }
    }
}

private val MediaVariant.resolution: Int?
    get() {
        val dimensions = DIMENSIONS.find(quality)
        if (dimensions != null) return minOf(dimensions.groupValues[1].toInt(), dimensions.groupValues[2].toInt())
        return RESOLUTION
            .find(quality)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

private val MediaVariant.bitrate: Int?
    get() =
        BITRATE
            .find(quality)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

private val DIMENSIONS = Regex("(\\d+)\\s*[×xX]\\s*(\\d+)")
private val RESOLUTION = Regex("(\\d+)\\s*p", RegexOption.IGNORE_CASE)
private val BITRATE = Regex("(\\d+)\\s*kbps", RegexOption.IGNORE_CASE)
