# Dambom 프로젝트 분석

## 1. 현재 구조

- `app`은 배포 설정과 최종 DI composition root를 담당하고 `presentation`은 Activity, 앱 scaffold와 최상위 Navigation 조립을 담당합니다.
- `feature:* -> core:domain` 단방향 규칙을 사용하며 Repository 계약은 `core:domain`, 구현은 `core:data:repository`에 둡니다.
- 공통 Retrofit/OkHttp 인프라는 `core:network`, Sample API/DTO/RemoteDataSource는 `core:data:remote`에 분리돼 있습니다.
- Room database와 DAO/entity는 `core:database`, DataStore 설정 계약과 구현은 `core:datastore`가 소유합니다. Repository 구현은 DAO 또는 설정 계약을 직접 주입받습니다.
- Compose 기반 MVI `MviViewModel<State, Intent, Effect>`와 선택적으로 사용하는 `BaseViewModel`을 `core:common-ui`에서 제공합니다.
- Navigation 3는 `core:navigation`의 `NavigationState`, `Navigator`, `NavigationDispatcher`와 `presentation`의 단일 collector로 구성됩니다.
- Version Catalog와 기능별 convention plugin을 사용하며 debug, qa, release build type과 ktlint, detekt, lint를 구성합니다.
- Navigation EventBus, back stack, deep link, Room migration, mapper, Repository, UseCase와 ViewModel 동작을 각각 독립 테스트합니다.

## 2. 재사용 가능한 요소

- Domain Repository 계약과 Data 구현 분리
- feature가 entry provider를 제공하고 presentation이 `NavDisplay`를 조립하는 방식
- top-level별 독립 back stack과 synthetic deep link back stack
- `NavigationEvent -> 단일 collector -> Navigator -> NavigationState` 흐름
- stateless Screen, lifecycle-aware Flow 수집과 fake/spy 우선 테스트
- local Flow를 source of truth로 사용하고 remote refresh가 local 저장소를 갱신하는 offline-first 흐름
- MVVM과 MVI가 동일 Domain, Data와 Sample UI를 공유하는 비교 구조
- Version Catalog와 opt-in convention plugin의 역할 분리

## 3. 해결된 구조 문제

- Navigation EventBus 전달 계약과 Navigator back stack 정책을 별도 테스트로 분리했습니다.
- feature Screen은 ViewModel을 직접 참조하지 않고 Route가 state와 callback을 연결합니다.
- 서비스 API/DTO를 `core:data:remote`로 이동해 `core:network`를 공통 HTTP 인프라 경계로 제한했습니다.
- Hilt, Retrofit, Room, DataStore는 모든 Android library에 일괄 적용하지 않고 필요한 convention 또는 모듈에서만 적용합니다.
- MVVM/MVI 선택 기준을 문서화하고 두 방식이 동일한 `SampleScreen`을 사용하도록 구성했습니다.

## 4. 실제 프로젝트 적용 시 교체할 항목

- application ID, package, 앱 이름과 launcher asset
- custom scheme과 host 또는 검증된 HTTPS App Link 정책
- 실제 API base URL과 Release `SampleRemoteDataSource` binding
- Release와 QA signing configuration
- 서비스별 Analytics 구현과 이벤트 규칙
- 저장 데이터 민감도에 따른 backup, 암호화와 네트워크 보안 정책

이 항목들은 템플릿 구조의 결함이 아니라 실제 서비스 요구사항이 정해져야 설정할 수 있는 경계입니다.

## 5. 새 프로젝트 적용 원칙

- `app`은 패키징과 DI composition root, `presentation`은 앱 셸과 최상위 Navigation만 담당합니다.
- Domain은 Android, Navigation, DTO와 Entity를 참조하지 않습니다.
- Network, Room Entity, Domain, UI model을 분리하고 모듈 경계에서 mapper를 적용합니다.
- 한 번 실행하는 작업은 `suspend`, 지속적으로 변하는 데이터는 `Flow`로 노출합니다.
- 단순 Repository 전달만 하는 UseCase는 만들지 않고 재사용되는 비즈니스 규칙에만 사용합니다.
- convention plugin은 기능별 opt-in을 유지하고 제품별 의존성을 공통 Android library plugin에 넣지 않습니다.

Source: current project analysis, Android Architecture Guide, Android Navigation 3 recipes
