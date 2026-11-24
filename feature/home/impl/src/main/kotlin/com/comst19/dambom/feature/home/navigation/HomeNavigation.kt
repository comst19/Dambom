package com.comst19.dambom.feature.home.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.feature.home.HomeRoute
import com.comst19.dambom.feature.home.api.HOME_DETECTION_DESTINATION
import com.comst19.dambom.feature.home.api.HomeKey
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val homeNavSerializersModule: SerializersModule =
    SerializersModule {
        polymorphic(NavKey::class) {
            subclass(HomeKey::class)
        }
    }

/** Home feature가 소유한 NavKey와 Route의 entry를 앱 entry provider에 등록합니다. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.homeEntries(
    resultPlaceholder: @Composable () -> Unit,
    isResultPaneVisible: () -> Boolean,
    onResultPaneVisibilityChange: (Boolean) -> Unit,
    networkAccess: () -> NetworkAccessState,
) {
    entry<HomeKey>(
        metadata =
            ListDetailSceneStrategy.listPane(sceneKey = HomeKey, detailPlaceholder = { resultPlaceholder() }) +
                mapOf(HOME_DETECTION_DESTINATION to HomeKey),
    ) {
        HomeRoute(
            networkAccess = networkAccess(),
            isResultPaneVisible = isResultPaneVisible(),
            onResultPaneVisibilityChange = onResultPaneVisibilityChange,
        )
    }
}
