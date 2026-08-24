package com.comst19.dambom.feature.downloads.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.navigation.contract.HomeGraph.DownloadsKey
import com.comst19.dambom.feature.downloads.DownloadsRoute

fun EntryProviderScope<NavKey>.downloadEntries(networkAccess: NetworkAccessState) {
    entry<DownloadsKey> { DownloadsRoute(networkAccess) }
}
