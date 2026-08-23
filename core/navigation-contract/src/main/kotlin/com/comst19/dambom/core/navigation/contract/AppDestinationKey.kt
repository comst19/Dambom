package com.comst19.dambom.core.navigation.contract

import com.comst19.dambom.core.navigation.AppNavKey
import com.comst19.dambom.core.navigation.TopLevelNavKey
import kotlinx.serialization.Serializable

sealed interface AuthGraph : AppNavKey {
    /** 인증 전 독립 navigation stack의 root destination입니다. */
    @Serializable
    data object LoginKey : AuthGraph, TopLevelNavKey
}

sealed interface HomeGraph : AppNavKey {
    /** 앱의 시작 top-level이자 Home stack의 root destination입니다. */
    @Serializable
    data object HomeKey : HomeGraph, TopLevelNavKey

    /** Home stack에 표시하는 MVVM Sample destination입니다. */
    @Serializable
    data object SampleMvvmKey : HomeGraph

    /** Home stack에 표시하는 MVI Sample destination입니다. */
    @Serializable
    data object SampleMviKey : HomeGraph

    /** Home stack의 Sample 상세 destination이며 [id]를 Navigation 인자로 전달합니다. */
    @Serializable
    data class SampleDetailKey(
        val id: Long,
    ) : HomeGraph
}

sealed interface SettingsGraph : AppNavKey {
    /** Settings 독립 stack의 root destination입니다. */
    @Serializable
    data object SettingsKey : SettingsGraph, TopLevelNavKey
}

sealed interface SampleMatchingGraph : AppNavKey {
    /** Sample Matching 독립 stack의 root destination입니다. */
    @Serializable
    data object SampleMatchingKey : SampleMatchingGraph, TopLevelNavKey

    /** Sample Matching stack의 상세 destination입니다. */
    @Serializable
    data object SampleMatchingDetailKey : SampleMatchingGraph

    /** Profile Edit UI를 Matching stack에 표시해 Back 시 Matching Detail로 복귀하는 destination입니다. */
    @Serializable
    data object SampleMatchingProfileEditKey : SampleMatchingGraph
}

sealed interface SampleProfileGraph : AppNavKey {
    /** Sample Profile 독립 stack의 root destination입니다. */
    @Serializable
    data object SampleProfileKey : SampleProfileGraph, TopLevelNavKey

    /** Sample Profile stack의 Edit destination이며 Back 시 Profile root로 이동합니다. */
    @Serializable
    data object SampleProfileEditKey : SampleProfileGraph
}
