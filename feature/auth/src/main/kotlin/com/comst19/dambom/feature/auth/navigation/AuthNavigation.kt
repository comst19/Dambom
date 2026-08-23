package com.comst19.dambom.feature.auth.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.navigation.contract.AuthGraph.LoginKey
import com.comst19.dambom.feature.auth.LoginRoute

/** Auth feature가 소유한 NavKey와 Route의 entry를 앱 entry provider에 등록합니다. */
fun EntryProviderScope<NavKey>.authEntries() {
    entry<LoginKey> { LoginRoute() }
}
