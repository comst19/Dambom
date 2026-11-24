package com.comst19.dambom.feature.detection.api

import com.comst19.dambom.core.navigation.AppNavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("dambom.detection_result")
data class DetectionResultKey(
    val url: String,
    val snapshotId: String? = null,
    val requestId: String? = null,
    val sharedLink: Boolean = false,
) : AppNavKey
