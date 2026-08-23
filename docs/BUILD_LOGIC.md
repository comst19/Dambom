# Build Logic

## 역할

`gradle/libs.versions.toml`은 버전, library alias, plugin alias를 관리합니다. `build-logic`은 compileSdk/minSdk, Java/Kotlin 17, Compose, Kotlin Serialization, Hilt, Room, test, lint 설정을 재사용합니다.

제공 plugin:

- `dambom.android.application`
- `dambom.android.library`
- `dambom.android.compose`
- `dambom.android.feature`
- `dambom.android.hilt`
- `dambom.android.room`
- `dambom.android.test`
- `dambom.kotlin.library`
- `dambom.kotlin.serialization`

각 convention plugin 구현 클래스는 역할별 파일로 분리합니다. plugin ID와 구현 클래스의 package는 유지하고, 여러 plugin에서 공유하는 Android, Kotlin, JaCoCo, version catalog 설정만 `buildlogic.internal`에 둡니다.

Compose와 Kotlin Serialization은 독립 convention입니다. Compose 모듈이라고 Serialization을 자동 적용하지 않으며, `@Serializable` 타입을 선언하는 모듈만 `dambom.kotlin.serialization`을 적용합니다.

Feature convention에는 `core:domain`, `core:common-ui`, `core:designsystem`, `core:navigation`, lifecycle, 공통 test만 포함합니다. Retrofit, Room, DataStore와 제품별 SDK는 opt-in입니다.

## SDK 정책

최신 AndroidX 안정판의 AAR 요구사항에 따라 compileSdk는 37입니다. targetSdk는 동작 변경을 분리하기 위해 36, minSdk는 26입니다. AGP 9.3.1과 호환되는 최신 안정 Gradle 9.6.1을 사용하고 배포 checksum을 고정합니다.

## Build type

| Build type | Application ID | 앱 이름 | 축소 | 원격 소스 | 서명 |
|---|---|---|---|---|---|
| `debug` | `com.comst19.dambom.debug` | Dambom Dev | 미적용 | seed | debug |
| `qa` | `com.comst19.dambom.qa` | Dambom QA | R8 + resource shrinking | seed | debug |
| `release` | `com.comst19.dambom` | Dambom | R8 + resource shrinking | Retrofit | 프로젝트에서 배포 서명 연결 |

QA는 Release build type을 상속해 배포 빌드와 가까운 조건으로 검증합니다. 모든 Android library convention에도 QA variant를 생성하므로 `matchingFallbacks`에 의존하지 않습니다. 앱 이름은 `main`, `debug`, `qa` source set의 리소스 우선순위로 구분합니다.

## 품질 도구

모든 subproject에 ktlint와 Detekt를 적용하고 Android Lint는 dependency까지 검사합니다. JaCoCo를 기본 적용합니다. Dependency Analysis는 모듈 의존성 보고서, Versions Plugin은 안정 버전 후보 확인에 사용합니다.
