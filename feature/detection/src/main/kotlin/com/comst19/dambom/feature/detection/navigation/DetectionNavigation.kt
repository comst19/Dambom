package com.comst19.dambom.feature.detection.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.navigation.contract.HomeGraph.DetectionResultKey
import com.comst19.dambom.feature.detection.DetectionRoute

fun EntryProviderScope<NavKey>.detectionEntries(networkAccess: NetworkAccessState) {
    entry<DetectionResultKey> { key -> DetectionRoute(key.url, networkAccess) }
}
