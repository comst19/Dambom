package com.comst19.dambom.feature.detection.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.feature.detection.DetectionRoute
import com.comst19.dambom.feature.detection.api.DetectionResultKey
import com.comst19.dambom.feature.home.api.HOME_DETECTION_DESTINATION
import com.comst19.dambom.feature.home.api.HomeKey
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val detectionNavSerializersModule: SerializersModule =
    SerializersModule {
        polymorphic(NavKey::class) {
            subclass(DetectionResultKey::class)
        }
    }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.detectionEntries(networkAccess: () -> NetworkAccessState) {
    entry<DetectionResultKey>(
        metadata = { key ->
            ListDetailSceneStrategy.detailPane(sceneKey = HomeKey) + mapOf(HOME_DETECTION_DESTINATION to key)
        },
    ) { key ->
        DetectionRoute(
            key.url,
            networkAccess(),
            snapshotId = key.snapshotId,
            sharedLink = key.sharedLink,
            isSupportingPane = LocalListDetailSceneScope.current != null,
        )
    }
}
