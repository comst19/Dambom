package com.comst19.dambom.feature.web.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.feature.web.WebRoute
import com.comst19.dambom.feature.web.api.WebKey
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val webNavSerializersModule: SerializersModule =
    SerializersModule {
        polymorphic(NavKey::class) {
            subclass(WebKey::class)
        }
    }

fun EntryProviderScope<NavKey>.webEntries() {
    entry<WebKey> { key -> WebRoute(key.url, sharedLink = key.sharedLink) }
}
