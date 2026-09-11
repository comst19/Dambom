package com.comst19.dambom.feature.detection.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.navigation.contract.HOME_DETECTION_DESTINATION
import com.comst19.dambom.core.navigation.contract.HomeGraph.DetectionResultKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import com.comst19.dambom.feature.detection.DetectionRoute

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
            isSupportingPane = LocalListDetailSceneScope.current != null,
        )
    }
}
