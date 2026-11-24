package com.comst19.dambom.feature.web.api

import com.comst19.dambom.core.navigation.AppNavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("dambom.web")
data class WebKey(
    val url: String? = null,
    val sharedLink: Boolean = false,
) : AppNavKey
