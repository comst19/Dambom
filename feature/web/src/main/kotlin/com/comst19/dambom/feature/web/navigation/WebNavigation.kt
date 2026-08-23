package com.comst19.dambom.feature.web.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.WebKey
import com.comst19.dambom.feature.web.WebRoute

fun EntryProviderScope<NavKey>.webEntries() {
    entry<WebKey> { key -> WebRoute(key.url) }
}
