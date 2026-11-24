package com.comst19.dambom.feature.library.navigation

import com.comst19.dambom.core.navigation.AppNavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("dambom.private_library")
internal data object PrivateLibraryKey : AppNavKey

@Serializable
@SerialName("dambom.video_subtitle_transcript")
internal data class VideoSubtitleTranscriptKey(
    val id: String,
) : AppNavKey

@Serializable
@SerialName("dambom.video_subtitle")
internal data class VideoSubtitleKey(
    val id: String,
) : AppNavKey

@Serializable
@SerialName("dambom.video_subtitle_export")
internal data class VideoSubtitleExportKey(
    val id: String,
) : AppNavKey

@Serializable
@SerialName("dambom.video_compression")
internal data class VideoCompressionKey(
    val id: String,
) : AppNavKey

@Serializable
@SerialName("dambom.video_trim")
internal data class VideoTrimKey(
    val id: String,
) : AppNavKey
