package com.comst19.dambom.core.navigation.contract

import com.comst19.dambom.core.navigation.AppNavKey
import com.comst19.dambom.core.navigation.TopLevelNavKey
import kotlinx.serialization.Serializable

sealed interface HomeGraph : AppNavKey {
    /** 앱의 시작 top-level이자 Home stack의 root destination입니다. */
    @Serializable
    data object HomeKey : HomeGraph, TopLevelNavKey

}

sealed interface SettingsGraph : AppNavKey {
    /** Settings 독립 stack의 root destination입니다. */
    @Serializable
    data object SettingsKey : SettingsGraph, TopLevelNavKey
}
