# Dambom

새 Android 앱을 시작할 때 복사해 사용할 수 있는 Kotlin, Compose, 멀티모듈 템플릿입니다.

처음 사용하는 경우 [Dambom 시작 가이드](docs/GETTING_STARTED.md)를 따라 프로젝트 복사, 식별자 변경, Android Studio 설정과 첫 빌드를 진행하세요.

## 빠른 시작

프로젝트 루트에서 먼저 이름 변경 범위를 확인합니다.

```bash
python3 tools/rename_project.py \
  --project-name "My App" \
  --package-name "com.company.myapp"
```

결과가 맞으면 `--apply`로 적용하고 빌드합니다.

```bash
python3 tools/rename_project.py \
  --project-name "My App" \
  --package-name "com.company.myapp" \
  --apply

./gradlew ktlintCheck assembleDebug
```

Sample 구현이 필요하지 않으면 이름 변경 후 제거 범위를 먼저 확인합니다.

```bash
python3 tools/remove_samples.py
python3 tools/remove_samples.py --apply

./gradlew test lint assembleDebug
```

이 명령은 Sample feature와 연결된 Domain, Data, Room, deep link 및 전용 테스트를 제거합니다. Auth, Home, Settings, 공통 Navigation과 Snackbar 구조는 유지합니다. 자세한 범위는 [시작 가이드](docs/GETTING_STARTED.md#sample-코드-제거선택)를 참고하세요.

한글 앱 이름, convention plugin 접두어, 딥링크와 전체 검증 절차는 [시작 가이드](docs/GETTING_STARTED.md)에 정리되어 있습니다.

## 기술 스택

- Kotlin 2.4.10, Java 17
- AGP 9.3.1, Gradle 9.6.1, KSP 2.3.11
- Jetpack Compose BOM 2026.06.01, Material 3
- Navigation 3 1.1.5, Lifecycle 2.11.0
- Hilt 2.60.1
- Retrofit 3.0.0, OkHttp 5.4.0, Kotlin Serialization 1.11.0
- Room 2.8.4, DataStore 1.2.1
- JUnit 4, Coroutines Test, Turbine, Robolectric, MockWebServer
- Android Lint, ktlint, Detekt, Dependency Analysis, Versions Plugin
- Macrobenchmark, Baseline Profile, Roborazzi screenshot testing

버전은 2026-08-07 기준 Google Maven, Maven Central, Gradle Plugin Portal의 릴리스 메타데이터와 공식 문서를 대조했습니다. 기본적으로 안정 버전을 사용하며, Baseline Profile 1.5.0-beta01은 AGP 9.3 호환을 위해 사용합니다.

## 실행 환경

- JDK 17
- Android SDK Platform 37
- Android SDK Build Tools 37.0.0
- `ANDROID_HOME` 환경 변수

민감값이 들어갈 수 있는 `local.properties`를 템플릿에 포함하지 않습니다.

## 새 프로젝트로 초기화

이름 변경 스크립트는 프로젝트명, applicationId, namespace, Kotlin package와 디렉터리, 디자인 시스템 심볼, 딥링크, benchmark 대상, Room schema 경로와 convention plugin ID를 함께 변경합니다. 저장소 루트 디렉터리 이름은 직접 변경합니다.

Sample 없는 앱으로 시작하려면 이름 변경 후 `tools/remove_samples.py`를 실행합니다. 기본 실행은 dry-run이며 `--apply`를 지정할 때만 Sample 수직 슬라이스를 삭제합니다.

한글 앱 이름을 포함한 전체 옵션, dry-run과 적용 방법, Android Studio 설정, 첫 빌드와 GitHub 업로드 전 점검은 [Dambom 시작 가이드](docs/GETTING_STARTED.md)를 참고합니다.

## 모듈 구조

```text
app
├── presentation
│   └── feature:auth, feature:home, feature:sample, feature:settings
├── benchmarks, ui-test-manifest
└── core:common, core:common-ui, core:coroutine, core:designsystem,
    core:navigation, core:navigation-contract, core:domain, core:data:repository, core:data:remote,
    core:network, core:database, core:datastore,
    core:analytics, core:testing, core:test-fixture, core:screenshot-testing
```

의존성은 `app -> presentation -> feature -> core:domain/core UI`와 `app -> core:data -> core:domain/infrastructure` 방향입니다. `core:domain`은 Android 프레임워크를 참조하지 않습니다.

최상위 `presentation` 모듈은 Activity, 앱 scaffold, Navigation 조립과 시스템 UI 정책만 소유합니다. 각 `feature`는 자신의 Route, stateless Screen, ViewModel, UiState와 UI mapper를 함께 소유하며 `navigation` 패키지에서 Navigation 3 entry를 제공합니다. Sample 데이터는 `SampleResponse -> NetworkSample -> SampleEntity -> Domain Sample -> SampleUiModel` 경계를 거쳐 이동합니다.

앱 전역 snackbar는 `core:common-ui`의 `SnackbarEventBus`로 발행하고 presentation의 단일 `SnackbarHost`에서 순서대로 표시합니다. Edge-to-edge는 presentation scaffold가 전달한 패딩을 화면 특성에 맞게 소비합니다. Home은 일반 화면 modifier 패딩, Sample 목록은 `LazyColumn.contentPadding`, Sample Detail은 전체 화면 배경과 `safeDrawingPadding`, 선택적 상태 바 gradient protection 예제입니다. `AppChrome`은 실제로 시스템 바 뒤에 보이는 화면 유형의 배경을 기준으로 아이콘 명암을 설정합니다.

`feature:auth`는 시작 목적지와 `SetRoot(HomeKey)` 전환을 보여주는 로그인 골격입니다. 실제 인증, 토큰 저장과 세션 확인은 포함하지 않으며, 이를 추가할 때 `StartupCoordinator`가 인증 상태에 따라 시작 destination을 결정합니다.

`core:common-ui`에는 일반 화면 inset을 적용하는 `appScaffoldPadding()`과 연속 탭을 막으면서 기본 클릭 semantics를 유지하는 `throttledClickable()`이 포함되어 있습니다. 사용 기준과 예제는 [Architecture](docs/ARCHITECTURE.md#공통-modifier)를 참고합니다.

## Presentation 패턴 선택

- MVVM: 입력과 상태 전이가 단순한 CRUD, 설정 화면
- MVI: reducer, intent 순서, 일회성 effect 계약이 중요한 화면
- 한 feature의 한 화면에서는 한 패턴만 선택합니다.
- 두 Sample 구현은 같은 Domain/Data와 stateless Compose 화면을 공유합니다.

## 주요 명령

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew assembleQa
./gradlew assembleRelease
./gradlew test
./gradlew lint
./gradlew check
./gradlew buildHealth
./gradlew dependencyUpdates -Drevision=release
./gradlew :benchmarks:connectedBenchmarkReleaseAndroidTest
./gradlew :app:generateReleaseBaselineProfile
./gradlew recordRoborazziDebug
```

기본 build type은 `debug`, `qa`, `release`입니다. Debug와 QA는 각각 `.debug`, `.qa` applicationId suffix와 구분되는 앱 이름을 사용해 한 기기에 함께 설치할 수 있습니다. QA는 Release의 R8 설정을 상속하지만 템플릿 실행을 위해 seed 원격 소스를 사용합니다. `app` BuildConfig의 `APP_ENVIRONMENT`는 `core:common`의 `AppEnvironment`로 변환되어 필요한 모듈에 주입됩니다.

상세 문서는 [Getting Started](docs/GETTING_STARTED.md), [Architecture](docs/ARCHITECTURE.md), [Navigation](docs/NAVIGATION.md), [Presentation](docs/PRESENTATION_PATTERN.md), [Feature Guide](docs/FEATURE_GUIDE.md), [Edge-to-edge](docs/EDGE_TO_EDGE.md), [Database](docs/DATABASE.md), [Networking](docs/NETWORKING.md), [Error Handling](docs/ERROR_HANDLING.md), [Build Logic](docs/BUILD_LOGIC.md), [Testing](docs/TESTING.md), [Verification](docs/VERIFICATION.md), [Decisions](docs/DECISIONS.md)을 참고합니다.
