# Presentation Pattern

## 공통 규칙

- Screen은 state와 callback만 받는 stateless composable입니다.
- Route는 ViewModel 획득, lifecycle-aware state 수집과 effect 처리를 담당하고 Screen을 호출합니다.
- ViewModel은 lifecycle-aware `StateFlow`를 노출합니다.
- `NavigationEvent`는 화면 이동, `SnackbarEventBus`는 앱 전역 Snackbar, `UiEffect`는 특정 화면의 일회성 UI 동작을 담당합니다.
- feature 구현은 가능한 한 `internal`로 제한합니다.
- `EntryProviderScope` 확장은 feature의 `navigation` 패키지에 두고 Route만 호출합니다.

## MVVM

`SampleMvvmViewModel`은 명시적인 `onRefresh`, `onItemClick` 함수와 하나의 StateFlow를 제공합니다. 단순 CRUD, 폼, 설정처럼 상태 전이가 쉽게 읽히는 화면에 사용합니다.

## MVI

`SampleMviViewModel`은 `contract` 패키지의 `SampleIntent`를 받아 reducer로 `SampleState`를 변경합니다. 현재 화면 한정 Effect는 없지만 확장 위치를 보여주기 위해 빈 sealed `SampleEffect : UiEffect` 계약을 제공합니다. Effect가 필요해지면 이 계약에 타입을 추가합니다. 최상위 `UiEffect`를 직접 generic으로 사용하지 않아 feature별 출력 타입을 컴파일 단계에서 제한합니다. 입력 순서, 복잡한 상태 전이, effect 계약을 테스트해야 하는 화면에 사용합니다.

## 선택과 혼용 제한

팀은 feature 시작 시 상태 전이 복잡도를 기준으로 하나를 선택합니다. 한 화면에서 MVVM callback과 MVI intent를 동시에 공개하지 않습니다. 공통 Base에는 lifecycle, dispatcher, navigation, analytics처럼 실제로 공유하는 기능만 둡니다. 동일 Sample UI와 Domain/Data를 공유하므로 패턴 차이를 비교할 수 있습니다.

Refresh처럼 중복 실행 시 상태가 경합하는 작업은 `ViewModelJobLauncher<JobKey>.launchIfIdle(key)`로 실행 중인 동일 작업을 무시합니다. ViewModel별 `JobKey`에 작업을 추가할 수 있으며 서로 다른 key는 동시에 실행됩니다. 제네릭 key이므로 잘못된 key 타입이나 캐스팅이 필요하지 않습니다. UI click throttle은 입력 빈도만 줄일 뿐 작업 중복 방지 계약을 대신하지 않습니다.

Flow를 UI state로 즉시 변환할 때는 모든 emission을 순서대로 처리하는 `collect`를 기본으로 사용합니다. 검색처럼 처리 블록에 취소 가능한 suspend 작업이 있고 새 값이 이전 작업을 대체해야 할 때만 `collectLatest`를 선택합니다.
