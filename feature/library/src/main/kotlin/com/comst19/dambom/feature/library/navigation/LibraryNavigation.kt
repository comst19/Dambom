package com.comst19.dambom.feature.library.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.LibraryKey
import com.comst19.dambom.feature.library.LibraryRoute

fun EntryProviderScope<NavKey>.libraryEntries() {
    entry<LibraryKey> { LibraryRoute() }
}
