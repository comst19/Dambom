package com.comst19.dambom.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

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
 * 저장 상태 복원에 실패하면 top-level history와 모든 탭 back stack을 함께 [config] 초기 상태로 복구합니다.
 */
@Composable
fun rememberNavigationState(
    config: NavigationConfig,
    serializersModule: SerializersModule,
): NavigationState =
    // 구버전 스택은 복원하지 않고, 업데이트 후 저장한 내비게이션부터 이 슬롯에서 복원한다.
    key(NAVIGATION_STATE_SAVE_KEY) {
        val saver =
            remember(config, serializersModule) {
                navigationStateSaver(config = config, serializersModule = serializersModule)
            }
        rememberSaveable(config, saver = saver) {
            createInitialNavigationState(config)
        }
    }

private const val NAVIGATION_STATE_SAVE_KEY = "dambom.navigation_state"

/**
 * [NavigationState]의 top-level history와 탭별 back stack을 단일 [SavedState]로 저장·복원합니다.
 * 역직렬화나 상태 불변식 검증에 실패하면 `null`을 반환해 초기 상태로 일괄 복구되도록 합니다.
 */
fun navigationStateSaver(
    config: NavigationConfig,
    serializersModule: SerializersModule,
): Saver<NavigationState, Any> {
    val savedStateConfiguration =
        SavedStateConfiguration {
            this.serializersModule = serializersModule
        }
    return Saver(
        save = { state ->
            val snapshot =
                NavigationSavedSnapshot(
                    topLevelHistory = state.topLevelsInUse,
                    backStacks =
                        state.backStacks.map { (rootKey, stack) ->
                            SavedTabBackStack(
                                rootKey = rootKey,
                                stack = stack.toList(),
                            )
                        },
                )
            encodeToSavedState(
                serializer = NavigationSavedSnapshot.serializer(),
                value = snapshot,
                configuration = savedStateConfiguration,
            )
        },
        restore = { savedValue ->
            restoreNavigationState(
                savedValue = savedValue,
                config = config,
                savedStateConfiguration = savedStateConfiguration,
            )
        },
    )
}

internal fun createInitialNavigationState(config: NavigationConfig): NavigationState =
    createNavigationState(
        config = config,
        topLevelHistory = config.initialTopLevelKeys,
        stacksByRoot = config.topLevelKeys.associateWith { key -> listOf(key) },
    )

private fun createNavigationState(
    config: NavigationConfig,
    topLevelHistory: List<TopLevelNavKey>,
    stacksByRoot: Map<TopLevelNavKey, List<AppNavKey>>,
): NavigationState =
    NavigationState(
        bottomHomeKey = config.bottomHomeKey,
        bottomBarKeys = config.bottomBarKeys,
        topLevelHistory = NavBackStack<NavKey>().apply { addAll(topLevelHistory) },
        backStacks =
            config.topLevelKeys.associateWith { rootKey ->
                NavBackStack<NavKey>().apply { addAll(stacksByRoot.getValue(rootKey)) }
            },
        topLevelBackBehavior = config.topLevelBackBehavior,
    )

private fun restoreNavigationState(
    savedValue: Any,
    config: NavigationConfig,
    savedStateConfiguration: SavedStateConfiguration,
): NavigationState? =
    // 탭 이력과 스택이 어긋나지 않도록 스냅샷 전체를 초기화한다.
    (savedValue as? SavedState)
        ?.let { savedState ->
            runCatching {
                decodeFromSavedState(
                    deserializer = NavigationSavedSnapshot.serializer(),
                    savedState = savedState,
                    configuration = savedStateConfiguration,
                )
            }.getOrNull()
        }?.toNavigationStateOrNull(config)

private fun NavigationSavedSnapshot.toNavigationStateOrNull(config: NavigationConfig): NavigationState? {
    val restoredHistory = validatedTopLevelHistory(config)
    val stacksByRoot = validatedStacks(config)
    return if (restoredHistory == null || stacksByRoot == null) {
        null
    } else {
        createNavigationState(
            config = config,
            topLevelHistory = restoredHistory,
            stacksByRoot = stacksByRoot,
        )
    }
}

private fun NavigationSavedSnapshot.validatedTopLevelHistory(config: NavigationConfig): List<TopLevelNavKey>? {
    val typedHistory = topLevelHistory.filterIsInstance<TopLevelNavKey>()
    if (typedHistory.size != topLevelHistory.size) return null
    val isValid =
        when (typedHistory.size) {
            1 -> {
                typedHistory.first() in config.topLevelKeys
            }

            2 -> {
                val current = typedHistory.last()
                current in config.topLevelKeys &&
                    typedHistory ==
                    topLevelHistoryFor(
                        key = current,
                        bottomHomeKey = config.bottomHomeKey,
                        bottomBarKeys = config.bottomBarKeys,
                        topLevelBackBehavior = config.topLevelBackBehavior,
                    )
            }

            else -> {
                false
            }
        }
    return typedHistory.takeIf { isValid }
}

private fun NavigationSavedSnapshot.validatedStacks(config: NavigationConfig): Map<TopLevelNavKey, List<AppNavKey>>? {
    if (backStacks.size != config.topLevelKeys.size) return null
    val stacksByRoot = linkedMapOf<TopLevelNavKey, List<AppNavKey>>()
    val allValid =
        backStacks.all { tab ->
            val rootKey = tab.rootKey as? TopLevelNavKey
            val typedStack = tab.stack.filterIsInstance<AppNavKey>()
            val tabValid =
                rootKey != null &&
                    rootKey in config.topLevelKeys &&
                    rootKey !in stacksByRoot &&
                    typedStack.size == tab.stack.size &&
                    typedStack.firstOrNull() == rootKey &&
                    typedStack.drop(1).none { it is TopLevelNavKey }
            if (rootKey != null && tabValid) {
                stacksByRoot[rootKey] = typedStack
            }
            tabValid
        }
    return stacksByRoot.takeIf { allValid && it.keys == config.topLevelKeys }
}

@Serializable
internal data class NavigationSavedSnapshot(
    val topLevelHistory: List<NavKey>,
    val backStacks: List<SavedTabBackStack>,
)

@Serializable
internal data class SavedTabBackStack(
    val rootKey: NavKey,
    val stack: List<NavKey>,
)

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
        topLevelHistory.addAll(
            topLevelHistoryFor(
                key = key,
                bottomHomeKey = bottomHomeKey,
                bottomBarKeys = bottomBarKeys,
                topLevelBackBehavior = topLevelBackBehavior,
            ),
        )
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
