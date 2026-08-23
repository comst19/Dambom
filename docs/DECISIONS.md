# Decisions

## 기존 프로젝트에서 유지한 것

- NavigationEvent를 최상위 단일 collector가 처리하는 구조
- top-level별 독립 back stack
- feature가 Navigation 3 entry를 제공하고 app이 조립하는 방식
- Repository 계약과 구현의 분리

## 범용화하며 변경한 것

- navigation 명령을 suspend `dispatch`로 전송해 bounded buffer가 가득 찬 경우에도 조용히 유실하지 않음
- navigation queue와 stack 정책을 독립 테스트
- 공통 HTTP client와 Sample API/DTO 모듈 분리
- 모든 Android 모듈에 제품 의존성을 넣지 않는 opt-in convention
- 동일 UI/Domain/Data를 공유하는 MVVM/MVI 비교 샘플
- feature-first 분류를 일관되게 적용하기 위해 공유 Domain, Data, Navigation, UI 기반 모듈을 `core` 하위에 배치
- 최상위 `presentation` 모듈은 Activity와 앱 셸 조립만 소유하고, feature가 Route, Screen, ViewModel, UiState와 UI mapper를 소유
- feature Navigation 3 entry는 `navigation` 패키지로, MVI Intent/State/Effect는 `contract` 패키지로 분리
- Retrofit DTO와 Room entity가 공개 Domain 계약으로 노출되지 않도록 Network model, Entity와 mapper를 경계별로 분리
- 앱 전역 snackbar 이벤트는 `core:common-ui`의 `SnackbarEventBus`에서 발행하고 presentation의 단일 collector가 표시
- 화면별 `AppUiPolicy`가 전체 화면 여부, bottom bar와 시스템 바 아이콘 명암을 결정

## Trade-off

- Channel은 프로세스가 살아 있는 동안 buffer의 이벤트를 순서대로 보관하지만, 영구 저장소가 아니므로 프로세스 종료 뒤에는 복원하지 않습니다. Navigation은 복원할 상태가 아니라 현재 back stack을 바꾸는 명령만 Channel로 전달하며, saveable `NavigationState`를 별도로 복원합니다. Buffer가 가득 차면 suspend 전송이 대기해 유실을 숨기지 않습니다.
- 일반 탭 선택과 교차 top-level synthetic 이동은 모두 `TopLevelBackBehavior`로 history를 재구성합니다. synthetic 이동 전 top-level은 root로 정리한 뒤 history에서 제거합니다.
- feature별 sealed NavKey는 기능군을 표현하지만 고정 stack 소유권을 강제하지 않습니다. 일반 destination은 현재 활성 stack에 쌓고 synthetic stack은 event의 `topLevelKey`로 문맥을 명시해, Notification과 WebView처럼 여러 top-level에서 공유하는 화면을 지원합니다.
- 같은 destination Key도 어느 stack에 추가하는지에 따라 Back 목적지가 결정됩니다. 단, 화면 인자나 상태 복원 단위가 달라야 하면 문맥별 Key를 분리합니다.
- Snackbar 이벤트도 영구 상태가 아닌 일회성 명령이므로 buffered Channel을 사용합니다. Collector가 `showSnackbar` 완료 후 다음 이벤트를 받아 발행 순서와 backpressure를 유지합니다. `Indefinite` 메시지는 dismiss action으로 닫으며, Retry처럼 feature 동작을 실행하는 action은 화면 state와 callback이 소유합니다.
- Edge-to-edge의 presentation scaffold는 인셋 값을 전달만 하고 일반, 스크롤, 전체 화면이 각각 modifier padding, list contentPadding, safe control padding으로 소비합니다. 시스템 바 배경은 Material component나 화면 콘텐츠가 소유하고 경로 정책은 아이콘 명암만 명시합니다. 화면별 예제는 [Edge-to-edge](EDGE_TO_EDGE.md)에 둡니다.
- Now in Android와 Pokedex처럼 `core:database`가 Room database, Entity, DAO와 migration을 함께 소유하고 Repository 구현이 DAO에 단방향으로 의존합니다. 이 선택은 순환 의존성과 pass-through local 추상화를 없애지만, 모든 앱 schema 변경이 `core:database`와 의존 Data 모듈의 재빌드를 유발하고 기능별 schema 소유권을 분리하지 못합니다. 하나의 앱과 하나의 Room database를 제공하는 템플릿에서는 단순성을 우선하며, 독립 database나 기능별 소유 팀이 실제로 생길 때 모듈 분리를 다시 검토합니다.
- `NetworkSample`, `SampleEntity`, Domain `Sample`, `SampleUiModel`은 각 API, database, 앱과 UI 경계를 보호하기 위해 분리합니다. Room 타입은 Repository 구현 내부에서만 사용하고 공개 Domain 계약에는 노출하지 않습니다.
- 모든 build type은 실제 API가 설정되기 전까지 seed remote를 사용합니다. Retrofit 구현은 실제 base URL과 운영 정책을 연결할 때 교체할 수 있는 경계로 유지합니다.
- 최신 안정 AndroidX를 위해 compileSdk 37을 사용하지만 targetSdk 36을 유지해 런타임 정책 변경을 별도 마이그레이션으로 남깁니다.
- Dependency Analysis 3.18.0은 AGP 9.3.1을 공식 검증 범위로 아직 표시하지 않습니다. 실제 `buildHealth` 실행으로 동작을 검증하되 경고는 문서화합니다.
- 공통 Android library convention은 존재하지 않는 `consumer-rules.pro`를 강제하지 않습니다. 소비자 규칙이 필요한 라이브러리만 자체 build script에서 명시합니다.
- Dependency Analysis의 `api` 전환 권고는 public signature만으로 자동 적용하지 않습니다. 내부 구현 타입이 우연히 노출된 경우 모듈 캡슐화를 우선하고 계약을 먼저 정리합니다.
- build type은 Piece와 같은 `debug`, `qa`, `release`로 구성합니다. QA는 Release의 축소 설정을 상속하되 외부 환경 없이 샘플을 검증할 수 있도록 seed 원격 소스를 사용합니다.
- `presentation`을 top-level 모듈로 두어 app의 패키징/DI와 앱 셸 UI를 분리합니다. feature별 화면 로직은 presentation으로 끌어올리지 않아 `app -> presentation -> feature -> core` 의존 방향을 유지합니다.
- QA는 템플릿에서 즉시 설치할 수 있도록 debug signing을 사용합니다. 실제 프로젝트의 운영 및 QA 배포 인증서는 각 프로젝트에서 별도로 연결합니다.
