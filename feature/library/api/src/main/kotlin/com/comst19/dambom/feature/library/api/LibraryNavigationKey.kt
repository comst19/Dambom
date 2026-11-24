package com.comst19.dambom.feature.library.api

import com.comst19.dambom.core.navigation.AppNavKey
import com.comst19.dambom.core.navigation.TopLevelNavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("dambom.library")
data object LibraryKey : TopLevelNavKey

@Serializable
@SerialName("dambom.video_detail")
data class VideoDetailKey(
    val id: String,
    val isPrivate: Boolean = false,
) : AppNavKey
