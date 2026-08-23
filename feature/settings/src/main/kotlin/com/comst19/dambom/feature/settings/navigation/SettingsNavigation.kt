package com.comst19.dambom.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.navigation.contract.SettingsGraph.HelpKey
import com.comst19.dambom.core.navigation.contract.SettingsGraph.SettingsKey
import com.comst19.dambom.feature.settings.HelpRoute
import com.comst19.dambom.feature.settings.SettingsRoute

/** Settings feature가 소유한 NavKey와 Route의 entry를 앱 entry provider에 등록합니다. */
fun EntryProviderScope<NavKey>.settingsEntries() {
    entry<SettingsKey> { SettingsRoute() }
    entry<HelpKey> { HelpRoute() }
}
