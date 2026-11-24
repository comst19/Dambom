package com.comst19.dambom.feature.downloads.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.feature.downloads.DownloadsRoute
import com.comst19.dambom.feature.downloads.api.DownloadsKey
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val downloadsNavSerializersModule: SerializersModule =
    SerializersModule {
        polymorphic(NavKey::class) {
            subclass(DownloadsKey::class)
        }
    }

fun EntryProviderScope<NavKey>.downloadEntries(networkAccess: () -> NetworkAccessState) {
    entry<DownloadsKey> { DownloadsRoute(networkAccess()) }
}
