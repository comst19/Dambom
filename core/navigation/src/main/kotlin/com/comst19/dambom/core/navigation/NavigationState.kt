package com.comst19.dambom.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/** 현재 top-level root에서 Back 했을 때 앱이 종료 경계에 도달하는 방식을 선택합니다. */
enum class TopLevelBackBehavior {
    /** 어느 top-level이든 현재 root에서 바로 앱의 공통 루트 Back 처리로 넘어갑니다. */
    ExitFromCurrent,

    /** 현재 root가 Home이 아니면 Home으로 이동한 뒤 종료 경계에 도달합니다. */
    ExitThroughHome,
}

/**
 * configuration change와 process recreation에 저장되는 앱 navigation 상태를 생성합니다.
 * 앱 셸에서 한 번 호출하며 [NavigationConfig.startKey]는 반드시
 * [NavigationConfig.topLevelKeys]에 포함해야 합니다.
 */
@Composable
fun rememberNavigationState(config: NavigationConfig): NavigationState {
    val topLevelHistory = rememberNavBackStack(*config.initialTopLevelKeys.toTypedArray())
    val backStacks = config.topLevelKeys.associateWith { key -> rememberNavBackStack(key) }
    return remember(config) {
        NavigationState(
            bottomHomeKey = config.bottomHomeKey,
            bottomBarKeys = config.bottomBarKeys,
            topLevelHistory = topLevelHistory,
            backStacks = backStacks,
            topLevelBackBehavior = config.topLevelBackBehavior,
        )
    }
}

/** top-level별 독립 stack과 현재 사용 중인 top-level history를 보관하는 상태 객체입니다. */
@Stable
class NavigationState(
    val bottomHomeKey: TopLevelNavKey,
    val bottomBarKeys: Set<TopLevelNavKey>,
    private val topLevelHistory: NavBackStack<NavKey>,
    val backStacks: Map<TopLevelNavKey, NavBackStack<NavKey>>,
    val topLevelBackBehavior: TopLevelBackBehavior,
) {
    /** 현재 화면을 소유하는 top-level key입니다. */
    val currentTopLevel: NavKey
        get() = topLevelHistory.last()

    /** 현재 top-level이 보유한 root부터 현재 화면까지의 stack입니다. */
    val currentStack: NavBackStack<NavKey>
        get() = checkNotNull(backStacks[currentTopLevel])

    /** 현재 화면을 나타내는 stack의 마지막 key입니다. */
    val currentKey: NavKey by derivedStateOf { currentStack.last() }

    /** 현재 stack과 top-level history 모두 더 이상 pop할 항목이 없는 앱 종료 경계인지 나타냅니다. */
    val isAtRoot: Boolean by derivedStateOf {
        currentStack.size == 1 && topLevelHistory.size == 1
    }

    /** [NavDisplay]에 전달해야 하는 top-level을 Back 순서대로 반환합니다. */
    val topLevelsInUse: List<NavKey>
        get() = topLevelHistory.toList()

    /**
     * 일반 바텀 탭 선택처럼 기존 임시 top-level history를 끝내고 [key]를 선택합니다.
     * [topLevelBackBehavior]가 [TopLevelBackBehavior.ExitThroughHome]이면 Home도 Back 경로에 유지합니다.
     */
    fun selectTopLevel(key: TopLevelNavKey) {
        require(key in backStacks)
        topLevelHistory.clear()
        if (
            topLevelBackBehavior == TopLevelBackBehavior.ExitThroughHome &&
            key in bottomBarKeys &&
            key != bottomHomeKey
        ) {
            topLevelHistory.add(bottomHomeKey)
        }
        topLevelHistory.add(key)
    }

    /** 현재 top-level root에서 이전 top-level로 돌아갑니다. 이전 항목이 없으면 상태를 유지합니다. */
    fun popTopLevel() {
        if (topLevelHistory.size > 1) topLevelHistory.removeLastOrNull()
    }

    /** 로그인·로그아웃처럼 앱 navigation 세션을 교체할 때 모든 stack을 root로 초기화합니다. */
    fun setRoot(key: TopLevelNavKey) {
        require(key in backStacks)
        backStacks.forEach { (root, stack) ->
            stack.clear()
            stack.add(root)
        }
        topLevelHistory.clear()
        topLevelHistory.add(key)
    }
}

/**
 * 모든 독립 stack을 saveable state와 ViewModelStore decorator가 적용된 [NavEntry]로 변환합니다.
 * 앱 셸에서 반환값을 `NavDisplay(entries = ...)`에 전달합니다.
 */
@Composable
fun NavigationState.toEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): SnapshotStateList<NavEntry<NavKey>> {
    val decoratedEntries =
        backStacks.mapValues { (_, stack) ->
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider = entryProvider,
            )
        }
    return topLevelsInUse
        .flatMap { key -> decoratedEntries[key] ?: emptyList() }
        .toMutableStateList()
}
