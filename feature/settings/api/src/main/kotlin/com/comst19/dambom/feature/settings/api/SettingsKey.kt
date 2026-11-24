package com.comst19.dambom.feature.settings.api

import com.comst19.dambom.core.navigation.AppNavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("dambom.settings")
data object SettingsKey : AppNavKey
