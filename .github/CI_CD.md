# Dambom CI/CD

## Branch flow

1. `feature/*`, `fix/*`, `chore/*` 브랜치는 `develop`에서 만든다.
2. 일반 변경은 Pull Request로 `develop`에 병합한다.
3. 출시 후보는 `develop`에서 `release`로 Pull Request를 만든다.
4. 검증된 출시는 `release`에서 `main`으로 Pull Request를 만든다.

GitHub Ruleset에서 `develop`, `release`, `main`의 직접 push를 막고 `Quality gate`를 필수 검사로 지정한다. 주기적인 Baseline Profile bot 커밋만 `develop` 직접 push를 허용하는 bypass 대상으로 둔다.

## Workflows

- `android-ci.yml`: `develop` push와 `develop`, `release`, `main` 대상 PR에서 정적 분석, 단위 테스트, lint·dependency analysis, Debug APK 빌드를 네 개의 matrix job으로 병렬 실행한다. `release`·`main` 승격 PR은 `versionCode` 증가도 검사하고, 마지막 `Quality gate`가 모든 결과를 하나의 필수 검사로 집계한다.
- `release-aab.yml`: `release` push 또는 수동 실행에서 release gate를 통과한 signed AAB와 R8 metadata를 30일 artifact로 보관한다.
- `baseline-profile.yml`: 매주 월요일 02:00 KST 또는 수동 실행에서 API 35 Managed Virtual Device로 Baseline Profile을 생성하고 변경이 있을 때만 `develop`에 bot 커밋한다.

Baseline Profile 자동 커밋은 `[skip ci]`를 포함한다. 현재 workflow는 schedule과 manual trigger만 사용하고, 기본 `GITHUB_TOKEN`으로 생성한 push는 다른 workflow를 다시 실행하지 않는다. PAT로 전환하더라도 GitHub의 `[skip ci]` 처리로 push·Pull Request workflow 재귀 실행을 차단한다.

`[skip ci]`는 자동 direct push에만 사용한다. 일반 Pull Request의 HEAD commit에 넣으면 필수 검사가 Pending으로 남아 병합을 막을 수 있으므로 사용하지 않는다.

## Required GitHub Secrets

| Secret | Description |
| --- | --- |
| `DAMBOM_KEYSTORE_BASE64` | Release upload keystore 파일 전체를 Base64로 인코딩한 값 |
| `DAMBOM_RELEASE_STORE_PASSWORD` | Keystore 비밀번호 |
| `DAMBOM_RELEASE_KEY_ALIAS` | Release key alias |
| `DAMBOM_RELEASE_KEY_PASSWORD` | Release key 비밀번호 |

`GITHUB_TOKEN`은 GitHub가 workflow마다 자동 발급하므로 별도 Secret 등록이 필요 없다. Keystore는 runner 임시 디렉터리에만 복원하고 workflow 종료 시 삭제한다.

## Baseline Profile and benchmarks

현재 `:macrobenchmark`는 Baseline Profile 생성과 앱 전체 Macrobenchmark를 함께 소유한다. 이 구조는 Now in Android와 Piece Android가 사용하는 방식이며 두 기능 모두 대상 앱을 별도 프로세스에서 실행하는 `com.android.test` producer이므로 분리할 필요가 없다.

함수나 작은 코드 경로를 측정하는 Microbenchmark를 추가할 때는 실행 모델이 다르므로 별도 `:microbenchmark` Android library 모듈로 만든다. EbbingPlanner의 `:baselineprofile` 모듈도 이름과 달리 `StartupBenchmarks`를 함께 포함하므로 Baseline Profile과 Macrobenchmark를 물리적으로 분리한 사례는 아니다.

로컬 물리 기기에서 프로필을 생성할 때는 다음 속성을 사용한다.

```bash
./gradlew :app:generateReleaseBaselineProfile \
  -Pdambom.baselineProfile.useConnectedDevices=true
```

## References

- [Piece Android CI](https://github.com/Piece-Puzzly/Piece-Android/blob/8ba095910c9a021d7dd3301287575444db1c1584/.github/workflows/android_ci.yml), [CD](https://github.com/Piece-Puzzly/Piece-Android/blob/8ba095910c9a021d7dd3301287575444db1c1584/.github/workflows/android_cd.yml), [setup action](https://github.com/Piece-Puzzly/Piece-Android/blob/8ba095910c9a021d7dd3301287575444db1c1584/.github/actions/setup-android-env/action.yml)
- [Now in Android build](https://github.com/android/nowinandroid/blob/07a8a171b679bc74d2963ea1ba929cbc5dc6079a/.github/workflows/Build.yaml), [nightly profiles](https://github.com/android/nowinandroid/blob/07a8a171b679bc74d2963ea1ba929cbc5dc6079a/.github/workflows/NightlyBaselineProfiles.yaml), [benchmarks](https://github.com/android/nowinandroid/blob/07a8a171b679bc74d2963ea1ba929cbc5dc6079a/benchmarks/build.gradle.kts)
- [EbbingPlanner profile workflow](https://github.com/tgyuuAn/EbbingPlanner/blob/576ee5b064919c198d4be70775d7ee2337af820b/.github/workflows/generate-baseline-profile.yml), [baselineprofile module](https://github.com/tgyuuAn/EbbingPlanner/blob/576ee5b064919c198d4be70775d7ee2337af820b/baselineprofile/build.gradle.kts)
