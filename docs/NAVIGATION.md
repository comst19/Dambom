# Navigation

## 구성

```text
ViewModel
  -> NavigationDispatcher.dispatch(event)
  -> bounded Channel
  -> presentation MainActivity의 단일 collector
  -> Navigator.handle(event)
  -> NavigationState
  -> NavDisplay
```

`core:navigation`은 Navigate, Replace, Back, PopTo, NavigateTopLevel, NavigateDeepLink 이벤트와 상태 변경 엔진을 제공합니다. `core:navigation-contract`는 이 앱의 직렬화 가능한 destination key를 소유하며, 각 key는 `AuthGraph.LoginKey`, `HomeGraph.HomeKey`처럼 기능 graph의 namespace 안에 선언합니다.

각 feature는 `navigation` 패키지에서 `EntryProviderScope<NavKey>` 확장 함수를 제공하고 자신의 Route에 NavKey를 연결합니다. `presentation`은 이 entry provider들을 조립할 뿐 feature Screen이나 ViewModel 구현을 소유하지 않습니다.

ViewModel 생성 시 destination 인자가 필요하면 Navigation 3 entry가 Hilt assisted factory의 `creationCallback`으로 `NavKey`를 전달해 ViewModel을 생성하고 Route에 주입합니다. ViewModel은 assisted key로 Flow를 즉시 구성하며 `LaunchedEffect`로 인자를 뒤늦게 전달하거나 별도 관찰 Job을 관리하지 않습니다. `rememberViewModelStoreNavEntryDecorator()`가 같은 NavEntry의 ViewModelStore를 유지하므로 back stack의 각 entry는 자신의 key와 ViewModel 상태를 보존합니다.

## EventBus 계약

- navigation 명령 전용이며 일반적인 global event bus로 확장하지 않습니다.
- Channel은 단일 소비자와 명령 순서를 보장합니다.
- bounded buffer와 suspend `dispatch`를 사용해 버퍼가 가득 찬 경우 발행 coroutine에 backpressure를 적용합니다.
- 장기 상태나 비즈니스 이벤트를 Channel에 저장하지 않습니다.
- 화면 중복 이동은 현재 key와 동일한 Navigate를 무시합니다.

### ViewModel에서 이벤트 발행

ViewModel은 `NavigationDispatcher`를 주입받고 사용자 동작 또는 비즈니스 결과가 확정됐을 때 이벤트를 발행합니다.

```kotlin
@HiltViewModel
class ExampleViewModel @Inject constructor(
    private val navigation: NavigationDispatcher,
) : ViewModel() {
    fun openDetail(id: Long) {
        viewModelScope.launch {
            navigation.dispatch(NavigationEvent.Navigate(SampleDetailKey(id)))
        }
    }
}
```

이벤트별 사용 기준은 다음과 같습니다.

```kotlin
// 현재 top-level stack에 하위 화면을 추가합니다.
navigation.dispatch(NavigationEvent.Navigate(SampleDetailKey(id)))

// 현재 화면을 교체해 교체 전 화면을 Back 경로에서 제거합니다.
navigation.dispatch(NavigationEvent.Replace(SampleDetailKey(id)))

// 코드에서 명시적으로 시스템 Back과 같은 동작을 실행합니다.
navigation.dispatch(NavigationEvent.Back)

// 기존 화면까지 돌아갑니다. inclusive = true면 대상 화면도 제거합니다.
navigation.dispatch(NavigationEvent.PopTo(SampleMvvmKey, inclusive = false))

// 바텀바 등에서 독립 top-level stack을 선택합니다.
navigation.dispatch(NavigationEvent.NavigateTopLevel(SettingsKey))

// 논리적 부모 화면을 포함하는 synthetic back stack을 한 번에 구성합니다.
navigation.dispatch(
    NavigationEvent.NavigateDeepLink(
        topLevelKey = HomeKey,
        backStack = listOf(HomeKey, SampleMvvmKey, SampleDetailKey(id)),
    ),
)
```

일반 시스템 Back은 `NavDisplay`가 처리하므로 화면마다 `NavigationEvent.Back`을 발행하지 않습니다. 저장 완료 후 자동으로 이전 화면으로 이동하는 것처럼 코드에서 명시적인 Back이 필요할 때만 사용합니다.

### 새 destination 추가 순서

기존 기능군에 하위 화면을 추가할 때는 해당 graph의 sealed interface 안에 key를 추가하고 entry에서 중첩 key를 import합니다.

```kotlin
sealed interface HomeGraph : AppNavKey {
    @Serializable
    data object NoticeKey : HomeGraph
}

fun EntryProviderScope<NavKey>.noticeEntries() {
    entry<HomeGraph.NoticeKey> { NoticeRoute() }
}
```

호출부에서는 일반 Navigate를 사용합니다.

```kotlin
navigation.dispatch(NavigationEvent.Navigate(HomeGraph.NoticeKey))
```

새 독립 stack을 추가할 때만 root key에 `TopLevelNavKey`를 구현하고 `AppNavigationConfig.topLevelDestinations`에 추가합니다. 바텀바에 표시하지 않을 top-level은 `bottomBarLabel`을 생략합니다.

## Back stack

각 top-level은 독립 stack을 유지합니다. 다른 top-level로 이동하면 기존 stack을 복원하고, 현재 top-level을 다시 선택하면 해당 stack의 root로 돌아갑니다. `currentTopLevel`은 공식 multiple-backstacks 레시피처럼 화면 back stack과 분리해 저장하며 `NavKey`를 `TopLevelNavKey`로 캐스팅하지 않습니다.

`AppNavKey`는 destination을, `TopLevelNavKey`는 독립 stack의 root를 표시합니다. 일반 destination은 현재 활성 top-level stack에 추가되므로 공용 화면을 여러 진입 문맥에서 같은 key로 사용할 수 있습니다.

```kotlin
sealed interface ProfileGraph : AppNavKey {
    @Serializable
    data object ProfileKey : ProfileGraph, TopLevelNavKey

    @Serializable
    data object ProfileEditKey : ProfileGraph
}
```

### Top-level과 Bottom bar

`TopLevelNavKey`는 독립 stack의 root를 의미하며 바텀바 노출을 의미하지 않습니다. Auth, Onboarding처럼 독립 stack은 필요하지만 바텀바에 표시하지 않는 destination도 top-level이 될 수 있습니다.

`AppNavigationConfig.topLevelDestinations`를 top-level 설정의 단일 원본으로 사용합니다. 문자열 리소스 label과 selected/unselected Material icon이 있는 destination만 바텀바에 표시합니다.

```kotlin
val topLevelDestinations =
    listOf(
        AppTopLevelDestination(
            key = HomeKey,
            bottomBarLabelRes = R.string.destination_home,
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
        ),
        AppTopLevelDestination(
            key = SettingsKey,
            bottomBarLabelRes = R.string.destination_settings,
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
        ),
        AppTopLevelDestination(OnboardingKey),
    )
```

이 목록에서 NavigationState의 `topLevelKeys`와 UI의 `bottomBarKeys`를 파생하므로 탭을 추가할 때 두 설정이 어긋나지 않습니다.

### Top-level Back 정책 선택

프로젝트 성격에 맞게 `AppNavigationConfig.navigation`을 구성해 core navigation에 주입합니다.

```kotlin
fun navigation(startKey: TopLevelNavKey) =
    NavigationConfig(
        startKey = startKey,
        bottomHomeKey = HomeKey,
        topLevelKeys = topLevelKeys,
        bottomBarKeys = bottomBarKeys,
        topLevelBackBehavior = TopLevelBackBehavior.ExitThroughHome,
    )
```

바텀바 라벨 리소스, 아이콘과 노출 여부는 navigation stack 동작이 아닌 presentation 정책이므로
`NavigationConfig`에 포함하지 않습니다.

앱 시작 시 `MainViewModel`이 `StartupCoordinator` 결과를 받을 때까지 시스템 스플래시를 유지합니다.
기본 구현은 지연 없이 `LoginKey`를 시작 화면으로 선택합니다.
로그인 버튼은 `SetRoot(HomeKey)`로 모든 기존 stack을 초기화한 뒤 Home으로 이동합니다.
인증 기능을 추가할 때는 coordinator의 시작 key 선택을 토큰 repository 확인으로 교체합니다.
네트워크 토큰 갱신 완료까지 시스템 스플래시를 유지하지 않습니다.

```kotlin
private fun initializeStartup() {
    viewModelScope.launch {
        val destination =
            suspendRunCatching { tokenRepository.checkTokenHealth() }
                .fold(
                    onSuccess = { HomeKey },
                    onFailure = { LoginKey },
                )

        _startupState.value = AppStartupState.Ready(destination)
    }
}
```

`ExitThroughHome`은 `startKey`와 별개인 `bottomHomeKey`를 통해 앱 루트에 도달합니다. 탭 방문 기록을 모두 쌓지 않고 Home과 현재 탭만 `NavDisplay`에 전달합니다.

```text
Home -> Settings -> Something
활성 top-level: [Home, Something]
Back: Something -> Home -> 루트 Back 안내 -> 두 번째 Back에서 종료
```

`ExitFromCurrent`는 각 탭을 독립적인 시작점으로 취급하는 서비스에 사용합니다. 현재 탭 stack만 `NavDisplay`에 전달하므로 탭 root에서 공통 루트 Back 정책을 적용합니다.

```text
Home -> Settings -> Something
활성 top-level: [Something]
Back: Something -> 루트 Back 안내 -> 두 번째 Back에서 종료
```

두 정책 모두 탭 내부 stack을 먼저 pop합니다. 예를 들어 `[Something, SomethingDetail]`에서는 `SomethingDetail -> Something` 순서로 이동한 뒤 선택한 top-level 정책을 적용합니다.

### 교차 Top-level synthetic back stack

사용자가 방문하지 않은 논리적 부모 화면을 Back 경로에 포함하려면 목적 top-level의 stack을 root부터 한 번에 구성합니다. 이동 문맥은 destination 자체가 아니라 event의 `topLevelKey`로 명시합니다.

```kotlin
NavigationEvent.NavigateDeepLink(
    topLevelKey = ProfileKey,
    backStack = listOf(ProfileKey, ProfileEditKey(userId)),
)
```

synthetic 이동은 이전 top-level stack을 root로 정리하고 Back history에서는 제거한 뒤 앱의 기본 `TopLevelBackBehavior`를 적용합니다.

```text
이동 전
top-level history: [Matching]
Matching stack: [Matching, MatchingDetail]

이동 후
top-level history: [Home, Profile]
Matching stack: [Matching]
Profile stack: [Profile, ProfileEdit]

Back
ProfileEdit -> Profile -> Home
```

Matching Detail에서 Profile synthetic 이동을 열면 Matching stack은 `[Matching]`으로 정리되고 top-level history에서는 제거됩니다. 따라서 Profile root 다음 Back은 이전 Matching이 아니라 앱에서 지정한 Home으로 이동하며, 이후 바텀바에서 Match를 선택해도 stale Detail이 아닌 Matching root가 표시됩니다.

synthetic stack의 첫 key는 event의 `topLevelKey`와 같아야 합니다. 이후 key는 해당 진입 문맥의 Back 경로를 표현합니다.

### 앱에서 예제 확인

Sample을 제거하기 전에는 바텀바의 `Match`와 `Profile` 탭에서 실제 교차 top-level 이동을 확인할 수 있습니다.

1. `Match` 탭을 선택합니다.
2. `Open Matching Detail`을 누릅니다.
3. `Edit: Back to Matching Detail`을 누르면 같은 Profile Edit UI가 Matching stack에 추가됩니다.
4. Back을 누르면 즉시 `Matching Detail`로 돌아갑니다.
5. 다시 Detail에서 `Edit: Back to Profile`을 누르면 Profile stack이 `[Profile, ProfileEdit]`로 구성됩니다.
6. 첫 Back은 `Profile root`, 두 번째 Back은 앱에서 지정한 `Home`으로 이동합니다.

샘플은 화면 설명 문구를 다르게 보여주기 위해 `SampleMatchingProfileEditKey`와 `SampleProfileEditKey`를 분리합니다. 실제 공용 화면은 같은 key를 현재 stack에 추가하거나, synthetic event의 `topLevelKey`로 다른 Back 문맥을 구성할 수 있습니다.

### 루트 Back 정책

백스택이 없는 루트에서 첫 Back은 Snackbar 안내를 표시합니다. 2초 안에 Back을 다시 누르면 `Activity.finish()`로 앱의 root Activity를 종료합니다. 루트가 아닐 때는 공통 `BackHandler`를 비활성화해 `NavDisplay`의 predictive back 진행률과 화면 전환을 그대로 사용합니다.

## 복원

Navigation 3의 `rememberNavBackStack`과 saveable/viewmodel entry decorator를 사용합니다. configuration change와 process recreation에서 직렬화 가능한 key가 복원됩니다. 소비 완료된 NavigationEvent는 복원하지 않습니다.

## 화면 전환

Navigation 3의 기본 scale 전환은 사용하지 않습니다. Piece와 동일하게 forward, back, predictive back 모두 700ms fade-in/fade-out을 적용해 화면 크기를 유지합니다. 전역 `NavDisplay`에서 지정하므로 모든 destination에 기본 적용됩니다.

## 사용 기준

ViewModel은 사용자 동작 또는 비즈니스 결과로 화면 전환이 결정될 때 event를 발행합니다. Snackbar, toast, animation은 UI effect로 처리합니다. child composable에는 화면 이동 callback을 연쇄 전달하지 않고 화면의 ViewModel 경계에서 dispatcher를 사용합니다.
