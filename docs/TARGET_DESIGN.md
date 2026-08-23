# 목표 구조 설계

## 최종 모듈

```text
app
core:common
core:common-ui
core:coroutine
core:designsystem
core:navigation
core:domain
core:data:repository
core:data:remote
core:network
core:database
core:datastore
core:analytics
core:testing
core:test-fixture
feature:home
feature:sample
feature:settings
```

`core:model`은 Domain model과 책임이 중복되고, 독립적으로 공유할 모델이 없어 만들지 않는다. `data` 루트 모듈도 구현 책임이 없어 만들지 않는다. 모든 생성 모듈은 샘플 앱에서 사용하거나 테스트/확장 지점으로 문서화한다.

## 의존성

```text
app -> feature + core:navigation + core:data:repository
feature -> core:domain + core:common-ui + core:designsystem + core:navigation
core:data:repository -> core:domain + core:data:remote + core:database + core:datastore
core:data:remote -> core:network
core:navigation -> Navigation 3 runtime
core:domain -> core:common
```

Presentation은 Domain 계약만 사용하고 app만 구현 모듈을 조립한다. Domain은 Android plugin을 적용하지 않는다.

## Navigation 흐름

```text
ViewModel -> NavigationDispatcher -> Channel -> App collector -> Navigator -> NavigationState -> NavDisplay
```

Channel은 단일 소비 명령 큐에 맞고 순서를 보장한다. suspend `dispatch`는 bounded buffer가 가득 차면 발행 coroutine을 대기시켜 명령 유실을 숨기지 않는다. config change 동안 app composition의 collector가 재생성되지만 saveable `NavigationState`가 복원 대상이며 이벤트는 장기 상태로 복원하지 않는다.

## Presentation 패턴

- MVVM: 단순 CRUD/설정 화면에 사용한다. 명시적인 사용자 함수와 `StateFlow`를 제공한다.
- MVI: 입력 순서, reducer, effect 계약이 중요한 화면에 사용한다.
- 두 패턴 모두 stateless `SampleScreen`과 동일 Domain/Data를 사용한다.
- Snackbar 같은 UI effect는 MVI effect channel, 화면 이동은 Navigation dispatcher로 분리한다.

## 기존 구조에서 가져올 요소와 개선

- 가져옴: top-level별 독립 back stack, 최상위 collector, feature별 entry 등록, Hilt composition.
- 개선: suspend 전송과 backpressure, event/state 테스트 분리, 공통과 feature 네트워크 모델 분리, opt-in convention plugin, Screen에서 ViewModel 직접 참조 제거.
