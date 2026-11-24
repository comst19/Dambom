package com.comst19.dambom.feature.home.api

import com.comst19.dambom.core.navigation.TopLevelNavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val HOME_DETECTION_DESTINATION = "dambom.homeDetectionDestination"

@Serializable
@SerialName("dambom.home")
data object HomeKey : TopLevelNavKey
