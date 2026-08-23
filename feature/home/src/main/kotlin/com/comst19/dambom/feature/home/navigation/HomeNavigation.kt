package com.comst19.dambom.feature.home.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import com.comst19.dambom.feature.home.HomeRoute

/** Home feature가 소유한 NavKey와 Route의 entry를 앱 entry provider에 등록합니다. */
fun EntryProviderScope<NavKey>.homeEntries() {
    entry<HomeKey> { HomeRoute() }
}
