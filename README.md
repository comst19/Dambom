# Dambom Android

웹에서 찾은 저장 권한이 있는 공개 비-DRM 영상과 공개 X 게시물의 MP4 영상을 감지하고 내려받아 보관하는 Android 앱입니다.

## 환경

- JDK 17
- Android SDK 37
- Kotlin, Jetpack Compose, Navigation 3
- Hilt, Room, DataStore, WorkManager, Media3

## 빌드

```bash
ANDROID_HOME=/path/to/android-sdk ./gradlew assembleDebug
```

## 검증

```bash
ANDROID_HOME=/path/to/android-sdk ./gradlew testDebugUnitTest lintDebug
```

## 브랜치와 커밋

- 기능과 설정 변경은 작업 브랜치에서 진행하고 검증 후 `main`에 병합합니다.
- 브랜치 예시: `feat/url-intake`, `feat/download-queue`, `design/mvp-shell`
- 커밋 접두사: `feat`, `fix`, `hotfix`, `refact`, `chore`, `design`, `test`, `docs`

## 지원 범위

사용자가 저장 권한을 가진 공개 비-DRM 영상만 지원합니다. DRM, 유료 콘텐츠, 로그인 또는 접근 제한을 우회하지 않습니다.
X 게시물은 공개 status ID만 FxTwitter API에 전달하며, 응답 중 `video.twimg.com`의 HTTPS MP4만 다운로드 후보로 사용합니다.
