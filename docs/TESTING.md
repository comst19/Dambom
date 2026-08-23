# Testing

## 계층별 전략

- Domain: UseCase 규칙과 취소 전파
- Repository: cache 정책, remote/local mapping, 취소 전파
- Network: MockWebServer와 실제 Retrofit serialization
- Room: in-memory DAO와 1 -> 2 migration
- DataStore: 임시 파일의 실제 serialization
- ViewModel: MVVM state/navigation, MVI intent/state/effect/navigation
- Navigation: event queue, dispatcher 실패, Navigator stack, deep link
- Compose: Robolectric 기반 렌더링과 클릭
- Screenshot: `core:screenshot-testing`의 Roborazzi 캡처 헬퍼
- Performance: `benchmarks`의 cold startup Macrobenchmark와 Baseline Profile 생성

`core:testing`에는 dispatcher rule, navigation spy, analytics fake를 두고 `core:test-fixture`에는 Domain repository fake와 fixture를 둡니다. `ui-test-manifest`는 Hilt UI 테스트용 빈 Activity를 제공합니다. 상태 기반 검증을 우선하며 외부 서버, 운영 DB, `Thread.sleep`을 사용하지 않습니다.

Macrobenchmark와 Baseline Profile은 Release applicationId인 `com.comst19.dambom`을 대상으로 실제 기기 또는 에뮬레이터에서 실행합니다. 일반 JVM/조립 검증에는 benchmark APK 컴파일만 포함하고 계측 실행은 별도 명령으로 수행합니다.

`FormFactorPreviews`는 Phone, Foldable, Tablet, Desktop을 한 번에 렌더링하고 `PhoneOrientationPreviews`는 portrait와 landscape를 비교합니다. 화면 Preview에 이 annotation을 붙여 폼 팩터별 레이아웃을 확인합니다.

Robolectric 4.16.1의 지원 범위에 맞춰 local Android test는 SDK 35에서 실행하며 제품 compileSdk 37과 분리합니다.

## Sample 제거 후 테스트

`tools/remove_samples.py --apply`는 Sample API, repository, DAO, use case, Screen과 ViewModel 테스트 및 Match/Profile Navigation 데모를 production Sample 코드와 함께 제거합니다. `NavigatorTest`와 `NavigationEventBusTest`는 Sample key가 아닌 test source 전용 key를 사용하므로 유지됩니다. 제거 후 `./gradlew test lint assembleDebug`로 공통 계약과 Home, Settings 앱 조립을 검증합니다.
