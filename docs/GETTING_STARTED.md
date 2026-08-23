# Dambom 시작 가이드

이 문서는 Dambom 템플릿을 복사해 새 프로젝트로 초기화하고 첫 Debug 빌드를 실행하는 절차를 설명합니다.

## 1. 템플릿에 포함된 것

- Kotlin, Jetpack Compose, Material 3
- Navigation 3와 top-level별 독립 back stack
- Feature 중심 멀티모듈 구조
- MVVM과 MVI 샘플
- Domain, Repository, Remote, Local 계층 예제
- Retrofit, OkHttp interceptor, Kotlin Serialization
- Room local source of truth와 DataStore
- Hilt dependency injection
- Debug, QA, Release build type
- Edge-to-edge 일반 화면, 목록, 전체 화면 샘플
- 앱 전역 snackbar event helper
- Scaffold inset과 연속 클릭 방지 공통 Modifier
- Unit, Compose, Room, Retrofit, migration 테스트
- ktlint, Detekt, Android Lint, Dependency Analysis
- Macrobenchmark, Baseline Profile, Roborazzi 기반 모듈

제품별 화면, 실제 API 주소, 토큰 기반 인증 구현, 운영 서명과 배포 설정은 포함하지 않습니다. `feature:auth`에는 시작 흐름과 로그인 성공 후 `SetRoot(HomeKey)` 전환만 보여주는 UI 골격이 포함됩니다.

## 2. 사전 준비

필요한 환경은 다음과 같습니다.

- Android Studio
- JDK 17
- Android SDK Platform 37
- Android SDK Build Tools 37.0.0
- Python 3

Android Studio에서 프로젝트를 열면 SDK 위치가 `local.properties`에 기록될 수 있습니다. 이 파일은 개인 환경 경로를 포함하므로 Git에 올리지 않으며 현재 `.gitignore`에 등록되어 있습니다.

터미널에서 빌드하려면 Android SDK 환경 변수를 설정합니다.

```bash
export ANDROID_HOME=/path/to/Android/sdk
```

macOS 기본 설치 위치를 사용하는 경우 예시는 다음과 같습니다.

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

## 3. 프로젝트 복사

템플릿 폴더를 원하는 위치로 복사합니다. 복사한 최상위 폴더 이름은 직접 변경해도 됩니다.

```text
new-project/
├── app/
├── build-logic/
├── core/
├── feature/
├── presentation/
├── tools/rename_project.py
├── settings.gradle.kts
└── gradlew
```

이후 모든 명령은 `settings.gradle.kts`와 `tools` 폴더가 보이는 프로젝트 루트에서 실행합니다.

```bash
cd /path/to/new-project
test -f tools/rename_project.py && echo "project root confirmed"
```

이미 `tools` 폴더 안에 있다면 `python3 rename_project.py`를 사용하거나 `cd ..`로 프로젝트 루트로 이동합니다. `tools` 안에서 `python3 tools/rename_project.py`를 실행하면 `tools/tools/rename_project.py`를 찾게 되어 실패합니다.

## 4. 프로젝트 식별자 결정

| 옵션 | 용도 | 예시 |
|---|---|---|
| `project-name` | 기기에 표시되는 앱 이름 | `오늘의 기록` |
| `code-name` | Kotlin 심볼과 Gradle 프로젝트 이름 | `DailyRecord` |
| `package-name` | applicationId, namespace, Kotlin package | `com.company.dailyrecord` |
| `scheme` | 딥링크 scheme | `dailyrecord` |
| `plugin-prefix` | convention plugin ID 접두어 | `daily-record` |

`code-name`은 영문 대문자로 시작하는 PascalCase여야 합니다. `package-name`, `scheme`, `plugin-prefix`는 영문 소문자로 시작해야 합니다.

영문 프로젝트명은 `code-name`, `scheme`, `plugin-prefix`를 생략하면 자동 생성됩니다.

```bash
python3 tools/rename_project.py \
  --project-name "Daily Record" \
  --package-name "com.company.dailyrecord"
```

한글 프로젝트명은 코드용 이름과 plugin 접두어를 명시합니다.

```bash
python3 tools/rename_project.py \
  --project-name "오늘의 기록" \
  --code-name "DailyRecord" \
  --package-name "com.company.dailyrecord" \
  --scheme "dailyrecord" \
  --plugin-prefix "daily-record"
```

이 단계는 dry-run입니다. 실제 파일은 변경하지 않고 변경될 파일과 경로만 출력합니다.

## 5. 이름 변경 적용

dry-run 결과가 맞으면 같은 명령에 `--apply`를 추가합니다.

```bash
python3 tools/rename_project.py \
  --project-name "오늘의 기록" \
  --code-name "DailyRecord" \
  --package-name "com.company.dailyrecord" \
  --scheme "dailyrecord" \
  --plugin-prefix "daily-record" \
  --apply
```

스크립트는 다음 항목을 함께 변경합니다.

- Gradle root project 이름
- applicationId와 namespace
- Kotlin package, import와 실제 package 디렉터리
- Application, Theme, 디자인 시스템 심볼
- 앱 표시 이름
- 딥링크 scheme
- Benchmark 대상 package
- Room schema package 경로
- Convention plugin ID와 구현 package

기본 적용 시 기존 `build`, `.gradle`, `.kotlin` 생성물을 제거해 이전 이름의 캐시가 남지 않게 합니다. 원본 템플릿 식별자가 남으면 스크립트가 오류로 종료됩니다.

이 스크립트는 원본 템플릿에서 한 번만 실행하도록 설계되어 있습니다. 이미 초기화된 프로젝트에서 다시 실행하면 원본 템플릿 검사에 실패합니다.

## Sample 코드 제거(선택)

실제 앱에서 Sample MVVM, MVI, Detail 구현을 사용하지 않으면 프로젝트 이름을 변경한 뒤 Sample 제거 범위를 확인합니다.

```bash
python3 tools/remove_samples.py
```

기본 실행은 dry-run입니다. 출력에는 삭제할 파일과 수정할 조립 코드가 표시되며 실제 파일은 변경하지 않습니다. Auth, Home과 Settings는 삭제 대상에 포함되지 않습니다.

범위가 맞으면 적용합니다.

```bash
python3 tools/remove_samples.py --apply
```

제거 대상은 다음과 같습니다.

- `feature:sample`
- Sample Domain model, repository 계약과 use case
- Sample remote API, data source와 DTO
- Sample repository 구현
- Sample 전용 Room database, DAO, entity와 schema
- Sample test fixture와 전용 테스트
- Sample Navigation key, entry, deep link와 Home 진입 버튼
- Sample Detail의 전체 화면 UI 정책과 전용 색상

`core:data:remote`, `core:database`, `core:test-fixture`는 현재 Sample 코드만 포함하므로 모듈도 함께 제거됩니다. 새 프로젝트에서 실제 remote 또는 Room 기능이 필요해질 때 해당 기능의 요구사항에 맞춰 모듈을 추가합니다.

다음 항목은 유지됩니다.

- `feature:auth`, `feature:home`, `feature:settings`
- 공통 Navigation state, dispatcher와 Back 정책
- 전역 Snackbar와 시스템 UI 구조
- Sample key와 분리된 공통 Navigation 테스트
- Network client, DataStore, analytics와 공통 test infrastructure

제거 후 검증합니다.

```bash
./gradlew test lint assembleDebug
```

Sample 제거 도구는 적용 후 다시 실행하면 오류로 종료됩니다. 삭제 대상을 이름으로 광범위하게 검색하지 않고 템플릿의 명시적인 Sample 수직 슬라이스만 변경합니다.

## 6. Android Studio 설정

1. Android Studio에서 복사한 프로젝트 루트를 엽니다.
2. Gradle JDK를 17로 선택합니다.
3. SDK Manager에서 Android SDK Platform 37과 Build Tools 37.0.0을 확인합니다.
4. Gradle Sync를 실행합니다.
5. 실행 configuration에서 `app`과 `debug` variant를 선택합니다.

프로젝트가 SDK 위치를 찾지 못하면 Android Studio에서 SDK를 지정하거나 터미널의 `ANDROID_HOME`을 확인합니다.

## 7. 첫 빌드

```bash
./gradlew projects
./gradlew ktlintCheck assembleDebug
```

성공한 Debug APK는 다음 위치에 생성됩니다.

```text
app/build/outputs/apk/debug/app-debug.apk
```

전체 품질 검증은 다음 명령으로 실행합니다.

```bash
./gradlew --no-parallel \
  ktlintCheck \
  check \
  assembleDebug \
  assembleQa \
  assembleRelease \
  :benchmarks:assemble
```

QA와 Release는 R8 및 resource shrinking을 사용하므로 Debug만으로 발견되지 않는 consumer rule이나 축소 오류도 확인합니다.

## 8. 샘플 화면 확인

앱을 실행하면 다음 예제를 확인할 수 있습니다.

1. Login
   - 실제 인증 없이 Home으로 전환하는 auth UI 골격
2. Home
   - 일반 화면의 Scaffold 패딩 적용
   - 전역 snackbar event helper
3. Sample MVVM 또는 Sample MVI
   - 같은 Domain과 Data를 사용하는 Presentation 패턴 비교
   - LazyColumn `contentPadding` 기반 edge-to-edge 목록
4. Sample 항목 선택
   - 시스템 바 뒤까지 배경을 그리는 전체 화면
   - 조작 영역의 `safeDrawingPadding`
   - 선택적 status bar gradient protection
5. Settings
   - DataStore 설정 저장
   - 런타임 dark theme와 시스템 바 아이콘 변경
6. Match와 Profile
   - top-level별 독립 back stack
   - `Profile Edit -> Matching Detail`과 `Profile Edit -> Profile -> Home` Back 경로 비교
   - 같은 UI를 Back 문맥별 destination Key로 분리하는 예제
## 9. Build type

| Build type | applicationId | 축소 | 데이터 소스 | 서명 |
|---|---|---|---|---|
| Debug | `<package>.debug` | 미사용 | Seed | Debug signing |
| QA | `<package>.qa` | R8 + resources | Seed | Debug signing |
| Release | `<package>` | R8 + resources | Seed | 프로젝트에서 설정 |

모든 build type은 외부 서버 없이 샘플을 실행할 수 있습니다. `core:network`는 debug, QA, release에 각각 별도의 base URL과 HTTP 로그 수준을 제공합니다. 템플릿 placeholder URL을 제품 서버 주소로 교체하고, Release는 로그 수준을 `NONE`으로 유지합니다. 이름 변경 후에는 링크의 package 경로도 새 package로 이동합니다.

운영 서명 정보와 인증서는 템플릿에 포함하지 않습니다. Release 배포 전에 프로젝트 정책에 맞는 signing configuration을 별도로 연결합니다.

## 10. 주요 수정 위치

| 목적 | 위치 |
|---|---|
| 앱 진입점과 최상위 Scaffold | `presentation/src/main/.../MainActivity.kt` |
| 전체 화면과 시스템 바 정책 | `presentation/src/main/.../AppUiPolicy.kt` |
| Navigation 상태와 이벤트 엔진 | `core/navigation` |
| 앱 destination key와 deep link 계약 | `core/navigation-contract` |
| 공통 UI 상태, app event와 Modifier | `core/common-ui` |
| Theme과 공통 Compose component | `core/designsystem` |
| Domain model, repository 계약, use case | `core/domain` |
| Repository 구현과 Network/Entity/Domain mapper | `core/data/repository` |
| Retrofit API와 remote model | `core/data/remote` |
| OkHttp, Retrofit과 interceptor | `core/network` |
| Room database, Entity, DAO와 migration | `core/database` |
| 화면별 Route, Screen, ViewModel과 계약 | `feature/*` |
| 공통 Gradle 설정 | `build-logic` |
| 의존성 버전 | `gradle/libs.versions.toml` |

Feature는 `core:domain`, 공통 UI와 Navigation에 의존하고 Data 구현 모듈을 직접 참조하지 않습니다. 자세한 경계는 [Architecture](ARCHITECTURE.md)와 [Feature Guide](FEATURE_GUIDE.md)를 참고합니다.

## 11. 테스트와 분석 명령

```bash
./gradlew test
./gradlew lint
./gradlew detekt
./gradlew ktlintCheck
./gradlew check
./gradlew buildHealth
./gradlew dependencyUpdates -Drevision=release --no-parallel
```

성능과 screenshot 관련 명령은 기기 또는 별도 기록 환경이 필요할 수 있습니다.

```bash
./gradlew :benchmarks:connectedBenchmarkReleaseAndroidTest
./gradlew :app:generateReleaseBaselineProfile
./gradlew recordRoborazziDebug
```

## 12. Private GitHub 저장소에 올리기 전

다음을 확인합니다.

- 프로젝트 이름 변경과 Debug 빌드가 성공했는가
- `local.properties`가 Git 대상에서 제외됐는가
- `.gradle`, `.idea`, `build` 생성물이 제외됐는가
- 실제 API key, 비밀번호, 토큰, 서명 파일이 없는가
- Release signing을 debug 인증서로 잘못 구성하지 않았는가
- 실제 API를 사용한다면 `https://example.invalid/`과 Release remote binding을 교체했는가

Git 초기화 전 포함 파일을 확인합니다.

```bash
git init
git status --short
git check-ignore local.properties
```

확인 후 첫 커밋을 만듭니다.

```bash
git add .
git status --short
git commit -m "Initial Android project setup"
```

GitHub에서 Private 저장소를 만든 뒤 표시되는 URL을 사용합니다.

```bash
git branch -M main
git remote add origin <private-repository-url>
git push -u origin main
```

## 13. 자주 발생하는 오류

### `tools/tools/rename_project.py`를 찾을 수 없음

현재 위치가 이미 `tools` 폴더입니다. 프로젝트 루트로 이동해 실행합니다.

```bash
cd ..
python3 tools/rename_project.py --help
```

### `plugin-prefix`가 올바르지 않음

한글 `project-name`에서는 영문 접두어를 명시합니다.

```bash
--plugin-prefix "daily-record"
```

### Android SDK를 찾을 수 없음

Android Studio SDK 설정 또는 환경 변수를 확인합니다.

```bash
echo "$ANDROID_HOME"
```

### 이름 변경 후 IDE에 이전 package가 보임

이름 변경 적용 시 생성 캐시는 기본적으로 제거됩니다. Android Studio에서 새 프로젝트 폴더를 다시 열고 Gradle Sync를 실행합니다.

## 관련 문서

- [Architecture](ARCHITECTURE.md)
- [Navigation](NAVIGATION.md)
- [Presentation Pattern](PRESENTATION_PATTERN.md)
- [Feature Guide](FEATURE_GUIDE.md)
- [Edge-to-edge](EDGE_TO_EDGE.md)
- [Database](DATABASE.md)
- [Networking](NETWORKING.md)
- [Error Handling](ERROR_HANDLING.md)
- [Build Logic](BUILD_LOGIC.md)
- [Testing](TESTING.md)
- [Verification](VERIFICATION.md)
- [Decisions](DECISIONS.md)
