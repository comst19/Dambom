package com.comst19.dambom.core.navigation

import com.comst19.dambom.core.navigation.contract.AppNavKey
import com.comst19.dambom.core.navigation.contract.TopLevelNavKey

/** ViewModel과 앱 셸 사이에서 전달하는 일회성 navigation 명령입니다. */
sealed interface NavigationEvent {
    /**
     * 현재 top-level stack에 새 화면을 추가합니다. 목록에서 상세 화면으로 이동할 때 사용합니다.
     * 현재 화면과 같은 key면 무시하며, [TopLevelNavKey]는 [NavigateTopLevel]처럼 처리합니다.
     */
    data class Navigate(
        val key: AppNavKey,
    ) : NavigationEvent

    /**
     * 현재 화면을 [key]로 교체합니다. 완료 화면처럼 기존 화면으로 Back 하면 안 되는 흐름에 사용합니다.
     * [key]가 top-level이면 해당 stack으로 이동하고 root만 남깁니다.
     */
    data class Replace(
        val key: AppNavKey,
    ) : NavigationEvent

    /**
     * 시스템 Back과 같은 이동입니다. 현재 stack을 먼저 pop하고, root에서는 top-level Back 정책을 적용합니다.
     */
    data object Back : NavigationEvent

    /**
     * 현재 stack에 이미 있는 [key]까지 돌아갑니다. [inclusive]가 true면 [key]도 함께 제거합니다.
     * [key]가 현재 stack에 없으면 아무 동작도 하지 않습니다.
     */
    data class PopTo(
        val key: AppNavKey,
        val inclusive: Boolean = false,
    ) : NavigationEvent

    /**
     * 바텀바 같은 top-level UI에서 stack을 전환할 때 사용합니다.
     * 다른 top-level은 기존 stack을 복원하고, 현재 top-level을 다시 선택하면 root로 돌아갑니다.
     */
    data class NavigateTopLevel(
        val key: TopLevelNavKey,
    ) : NavigationEvent

    /** 인증 상태 전환처럼 기존 navigation 이력을 모두 폐기하고 [key]를 새 root로 설정합니다. */
    data class SetRoot(
        val key: TopLevelNavKey,
    ) : NavigationEvent

    /**
     * 외부 딥링크나 직접 진입 흐름에서 목적지의 synthetic back stack을 한 번에 구성합니다.
     * [backStack]은 [topLevelKey]로 시작하며 이후 key는 해당 진입 문맥의 Back 경로를 표현합니다.
     * 이동 전 top-level은 Back history에서 제거하고 목적지 root 이후에는 앱의 top-level Back 정책을 적용합니다.
     */
    data class NavigateDeepLink(
        val topLevelKey: TopLevelNavKey,
        val backStack: List<AppNavKey>,
    ) : NavigationEvent
}
