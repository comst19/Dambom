package com.comst19.dambom.core.navigation

import com.comst19.dambom.core.navigation.contract.AppNavKey
import com.comst19.dambom.core.navigation.contract.TopLevelNavKey

/** [NavigationEvent]를 해석해 [NavigationState]만 변경하는 navigation 명령 처리기입니다. */
class Navigator(
    private val state: NavigationState,
) {
    /** 앱 셸의 단일 event collector에서 호출하며 이벤트 종류에 맞는 stack 연산으로 위임합니다. */
    fun handle(event: NavigationEvent) {
        when (event) {
            is NavigationEvent.Navigate -> navigate(event.key)
            is NavigationEvent.Replace -> replace(event.key)
            NavigationEvent.Back -> goBack()
            is NavigationEvent.PopTo -> popTo(event.key, event.inclusive)
            is NavigationEvent.NavigateTopLevel -> navigateTopLevel(event.key)
            is NavigationEvent.SetRoot -> setRoot(event.key)
            is NavigationEvent.NavigateDeepLink -> navigateDeepLink(event)
        }
    }

    /**
     * 일반 하위 destination을 현재 stack 끝에 추가합니다.
     * 같은 key는 중복 추가하지 않고, top-level key는 [navigateTopLevel]로 처리합니다.
     */
    fun navigate(key: AppNavKey) {
        if (key == state.currentKey) return
        if (key is TopLevelNavKey) {
            navigateTopLevel(key)
        } else {
            state.currentStack.add(key)
        }
    }

    /**
     * 현재 stack의 마지막 destination을 [key]로 교체합니다.
     * 완료 화면처럼 교체 전 화면이 Back 경로에 남으면 안 될 때 사용합니다.
     */
    fun replace(key: AppNavKey) {
        if (key is TopLevelNavKey) {
            navigateTopLevel(key)
            state.currentStack.clear()
            state.currentStack.add(key)
            return
        }
        state.currentStack.removeLastOrNull()
        state.currentStack.add(key)
    }

    /**
     * 현재 stack에서 마지막으로 등장한 [key]까지 pop합니다.
     * [inclusive]가 true면 대상도 제거하고, 대상이 없으면 아무 동작도 하지 않습니다.
     */
    fun popTo(
        key: AppNavKey,
        inclusive: Boolean,
    ) {
        val index = state.currentStack.indexOfLast { it == key }
        if (index < 0) return
        val firstRemoved = if (inclusive) index else index + 1
        if (firstRemoved < state.currentStack.size) {
            state.currentStack.subList(firstRemoved, state.currentStack.size).clear()
        }
        if (state.currentStack.isEmpty()) state.currentStack.add(state.currentTopLevel)
    }

    /**
     * 바텀바에서 [key]의 독립 stack으로 전환합니다.
     * 현재 top-level을 다시 선택하면 하위 destination을 모두 제거하고 root로 돌아갑니다.
     */
    fun navigateTopLevel(key: TopLevelNavKey) {
        if (key == state.currentTopLevel) {
            state.currentStack.subList(1, state.currentStack.size).clear()
        }
        state.selectTopLevel(key)
    }

    /** 모든 top-level stack과 history를 초기화하고 [key]를 새 앱 root로 설정합니다. */
    fun setRoot(key: TopLevelNavKey) {
        state.setRoot(key)
    }

    /**
     * 목적 top-level stack을 [NavigationEvent.NavigateDeepLink.backStack]으로 교체합니다.
     * 첫 key가 목적 top-level인지 검증하고 이전 top-level을 앱 Back history에서 제거합니다.
     */
    fun navigateDeepLink(event: NavigationEvent.NavigateDeepLink) {
        require(event.backStack.firstOrNull() == event.topLevelKey) {
            "A synthetic back stack must start with its top-level key"
        }
        state.currentStack.subList(1, state.currentStack.size).clear()
        state.selectTopLevel(event.topLevelKey)
        // 딥링크도 앱 내부에서 이동한 것처럼 뒤로 갈 수 있도록 synthetic back stack을 한 번에 교체합니다.
        state.currentStack.clear()
        state.currentStack.addAll(event.backStack)
    }

    /** 현재 stack의 하위 화면을 먼저 pop하고, root에서는 이전 top-level을 pop합니다. */
    fun goBack() {
        if (state.currentStack.size > 1) {
            state.currentStack.removeLastOrNull()
        } else {
            state.popTopLevel()
        }
    }
}
