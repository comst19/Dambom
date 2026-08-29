package com.comst19.dambom.core.navigation

import com.comst19.dambom.core.navigation.contract.TopLevelNavKey

/** 앱 셸이 주입하는 top-level navigation 구성입니다. */
data class NavigationConfig(
    val startKey: TopLevelNavKey,
    val bottomHomeKey: TopLevelNavKey,
    val topLevelKeys: Set<TopLevelNavKey>,
    val bottomBarKeys: Set<TopLevelNavKey>,
    val topLevelBackBehavior: TopLevelBackBehavior,
) {
    init {
        require(startKey in topLevelKeys) { "startKey must be included in topLevelKeys" }
        require(bottomHomeKey in topLevelKeys) { "bottomHomeKey must be included in topLevelKeys" }
        require(bottomHomeKey in bottomBarKeys) { "bottomHomeKey must be included in bottomBarKeys" }
        require(topLevelKeys.containsAll(bottomBarKeys)) { "bottomBarKeys must be included in topLevelKeys" }
    }

    /** 앱 시작 시 구성할 top-level Back 경로입니다. */
    val initialTopLevelKeys: List<TopLevelNavKey>
        get() =
            when {
                topLevelBackBehavior == TopLevelBackBehavior.ExitThroughHome &&
                    startKey in bottomBarKeys &&
                    startKey != bottomHomeKey -> listOf(bottomHomeKey, startKey)

                else -> listOf(startKey)
            }
}
