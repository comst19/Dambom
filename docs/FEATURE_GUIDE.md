# Feature Guide

## 추가 절차

1. `feature:<name>` 모듈을 만들고 `dambom.android.feature`를 적용합니다.
2. MVVM 또는 MVI 중 하나를 선택합니다.
3. Domain model과 Repository contract를 `core:domain`에 정의합니다.
4. 의미 있는 비즈니스 규칙만 UseCase로 정의합니다.
5. DTO/API와 RemoteDataSource를 `core:data:remote`에 추가합니다.
6. 구조화 데이터는 Entity/DAO를 `core:database`에, 설정 데이터는 DataStore 구현을 `core:datastore`에 추가합니다. 저장 구현 교체나 여러 저장소 조합이 실제로 필요할 때만 데이터별 local data source 계약을 둡니다.
7. mapper와 Repository 구현을 `core:data:repository`에 추가합니다.
8. Hilt binding을 app composition root에 연결합니다.
9. serializable NavKey와 `navigation` 패키지의 feature entry provider를 추가합니다.
10. unit, integration, Compose UI test와 Preview를 작성합니다.

## 권장 패키지 구조

```text
feature/<name>/
├── <Name>Screen.kt          # Route와 stateless Screen
├── <Name>ViewModel.kt
├── contract/
│   ├── <Name>State.kt
│   ├── <Name>Intent.kt      # MVI일 때
│   └── <Name>Effect.kt      # MVI일 때
└── navigation/
    └── <Name>Navigation.kt  # EntryProviderScope 확장
```

Route는 ViewModel, lifecycle-aware state 수집과 effect 처리를 소유합니다. Screen은 plain state와 callback만 받아 state hoisting을 유지합니다. `navigation` 패키지는 NavKey를 Route에 연결하는 역할만 담당합니다.

NavKey 인자가 ViewModel 초기화에 필요하면 `@AssistedInject` ViewModel과 `@AssistedFactory`를 사용합니다. `navigation` entry에서 `hiltViewModel<VM, Factory>(creationCallback = ...)`으로 ViewModel을 생성해 Route에 전달하고, ViewModel의 `observe(id)`를 `LaunchedEffect`로 호출하는 형태는 사용하지 않습니다.

## 데이터와 UI 상태

- 한 번 실행하는 조회, 저장, 삭제, 새로고침은 main-safe `suspend` 함수로 노출합니다.
- Room, DataStore처럼 값 변경을 계속 반영해야 하는 데이터는 `Flow`로 노출합니다.
- offline-first 조회는 local Flow를 source of truth로 사용하고 remote refresh는 local 저장소만 갱신합니다.
- Repository 값과 화면 상태가 1:1이면 `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue)`으로 ViewModel의 `StateFlow`를 만듭니다.
- 목록, 로딩, 오류처럼 여러 입력을 하나의 UI state에 합치면 `MutableStateFlow`를 두고 collect 결과와 사용자 작업 결과를 `update`로 반영합니다.
- 기존 상태와 무관하게 전체 값을 교체할 때만 `state.value = value`를 사용합니다. 기존 상태를 `copy`해 일부 필드를 바꿀 때는 동시 갱신 유실을 막기 위해 `update`를 사용합니다.

단순 Repository 호출을 감싸는 UseCase는 만들지 않습니다. 여러 Repository를 조합하거나 여러 ViewModel에서 재사용하는 비즈니스 규칙, 복잡한 정책을 캡슐화할 때만 UseCase를 추가합니다.

## ViewModel 작업

- ViewModel 작업은 `viewModelScope.launch`를 명시하고 이를 이름만 줄인 공통 `launch` 함수로 감싸지 않습니다.
- ViewModel은 `viewModelScope` 안에서 suspend 호출을 `suspendRunCatching`으로 감싸 성공과 실패를 UI state 또는 effect로 변환합니다.
- 성공과 실패는 Kotlin 표준 `Result.fold`로 UI state 또는 effect에 명시적으로 반영합니다. 화면이 처리할 수 없는 실패만 `ErrorHandler.handle(error)`로 전달합니다.
- `CancellationException`은 실패 결과로 변환하지 않고 재전파합니다.

API, RemoteDataSource와 CallAdapter 구성은 [Networking](NETWORKING.md), 계층별 예외와 화면/전역 처리 기준은 [Error Handling](ERROR_HANDLING.md)을 참고합니다.

## 제한

- Navigation 3 때문에 feature api/impl 분리를 강제하지 않습니다.
- Navigation EventBus를 일반 event bus로 사용하지 않습니다.
- Screen에서 DAO/API를 직접 호출하지 않습니다.
- Domain에 Android 타입, DTO, Entity, NavKey를 넣지 않습니다.
- 단순 전달 UseCase나 단일 사용 추상화를 대량 생성하지 않습니다.
- Retrofit, Room, DataStore는 필요한 모듈만 직접 의존합니다.
- deprecated API와 실제 비밀값을 템플릿에 포함하지 않습니다.
