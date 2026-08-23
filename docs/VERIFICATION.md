# Verification

검증일: 2026-08-07

## 빌드 및 정적 분석

| 명령 | 결과 |
|---|---|
| `./gradlew clean` | 성공 |
| `./gradlew assembleDebug test` | 성공 |
| `./gradlew lint` | 성공 |
| `./gradlew detekt` | 성공 |
| `./gradlew ktlintCheck` | 성공 |
| `./gradlew check assembleDebug` | 성공 |
| `./gradlew assembleQa` | 성공, R8 및 리소스 축소 포함 |
| `./gradlew assembleRelease` | 성공, R8 및 리소스 축소 포함 |
| `./gradlew buildHealth` | 성공, 비치명적 의존성 조언 보고서 생성 |
| `./gradlew dependencyUpdates -Drevision=release --no-parallel` | 안정 버전 추가 업데이트 없음 |
| `./gradlew projects --quiet` | 공통 모듈이 `core` 하위에 등록된 구조 확인 |
| `./gradlew --no-parallel ktlintCheck check assembleDebug assembleQa assembleRelease :benchmarks:assemble` | 성공, presentation 분리 후 2,605개 작업 완료 |
| `./gradlew --no-parallel :app:ktlintCheck :app:detekt :app:compileDebugKotlin :core:common-ui:testDebugUnitTest` | 성공, 앱 이벤트와 edge-to-edge 정책 정적 분석 및 이벤트 순서 테스트 포함 |
| 변경 모듈 ktlint, Detekt, `:feature:sample:testDebugUnitTest`, `:app:compileDebugKotlin` | 성공, 일반 화면, 목록 contentPadding, 전체 화면 safe control padding 샘플 검증 |
| `:core:common-ui`와 `:feature:sample` ktlint, Detekt, unit test, `:app:compileDebugKotlin` | 성공, MVI marker contract와 `AsyncUiState` 이름 분리 검증 |
| `./gradlew --no-parallel buildHealth` | 성공, 신규 모듈을 포함한 전체 의존성 분석 완료 |
| `./gradlew -p build-logic compileKotlin validatePlugins` | 성공, convention plugin 컴파일 및 plugin metadata 검증 |
| `./gradlew --no-parallel ktlintCheck :app:assembleDebug :app:assembleQa :app:assembleRelease` | 성공, build-logic 평탄화 후 앱 전체 build type 조립 |
| 임시 복사본에서 `tools/rename_project.py --project-name "My App" --package-name "com.acme.myapp" --apply` | 성공, 164개 파일과 38개 package 경로 및 5개 브랜드 경로 변경, presentation 포함 이전 식별자 없음 |
| 이름 변경 임시 복사본에서 build-logic `validatePlugins`, `ktlintCheck`, `:app:assembleDebug` | 성공, presentation과 평탄화된 build-logic package 이동 및 앱 조립 확인 |

로컬 테스트는 앱 이벤트 순서, 공통 Modifier, 네트워크 interceptor와 오류 변환, Domain, Repository, Retrofit, Room DAO와 migration, DataStore, Navigation, MVVM, MVI, Compose 렌더링을 포함합니다.

## 모듈 구조 검증

- Domain: `:core:domain`
- Data: `:core:data:repository`, `:core:data:remote`, `:core:database`
- Navigation: `:core:navigation`
- 앱 셸 Presentation: `:presentation`
- 공통 UI: `:core:designsystem`, `:core:common-ui`
- 검증 기반: `:benchmarks`, `:core:screenshot-testing`, `:ui-test-manifest`
- 기존 최상위 `:domain`, `:data:*` Gradle 참조와 이전 Kotlin 패키지 참조 없음
- 최상위 `domain`, `data` 디렉터리 없음

## 실행 검증

- Android Emulator에 `app-debug.apk` 설치 성공
- `MainActivity` cold start 성공
- 앱 프로세스 생존 확인
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- QA APK: `app/build/outputs/apk/qa/app-qa.apk`
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

QA APK 정적 검증 결과 applicationId는 `com.comst19.dambom.qa`, 앱 이름은 `Dambom QA`, 서명은 Android debug certificate입니다. 2026-08-07 후속 검증 시 에뮬레이터가 연결되지 않아 QA APK 설치 실행은 생략했습니다.

## 도구 호환성 메모

Dependency Analysis 3.18.0은 AGP 9.3.1을 아직 공식 검증 범위로 표시하지 않지만 실제 전체 모듈 분석은 성공했습니다. 생성된 조언에는 convention plugin이 공통 제공하는 테스트 도구와 Hilt/KSP 생성 코드 때문에 발생하는 정적 분석 오탐이 포함됩니다. 모듈 API 캡슐화를 약화시키는 `api` 전환은 자동 적용하지 않고 보고서를 검토 자료로 유지합니다.

Gradle 10 제거 예정 경고인 `ReportingExtension.file(String)` 호출은 Dependency Analysis plugin 구성 과정에서 발생합니다. 프로젝트 build logic은 해당 API를 직접 사용하지 않습니다.

병렬 전체 검증에서는 KSP QA 캐시와 Android Lint가 같은 생성 파일을 동시에 접근해 일시적인 `FileNotFoundException`이 발생할 수 있습니다. `clean` 후 `--no-parallel`로 동일 검증을 실행하면 성공하며 제품 코드나 테스트 실패는 아닙니다.
