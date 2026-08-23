# Error Handling

## 원칙

- 외부 라이브러리 예외는 경계에서 앱이 정의한 타입으로 변환합니다.
- HTTP 응답 오류, transport 오류와 decoding 오류를 서로 합치지 않습니다.
- 화면이 UX를 결정할 오류만 feature에서 처리하고 나머지는 공통 처리기로 전달합니다.
- 코루틴 취소는 실패로 변환하지 않습니다.

네트워크 구성과 CallAdapter 동작은 [Networking](NETWORKING.md)을 참고합니다.

## 계층별 오류

| 원인 | Remote | Domain |
|---|---|---|
| HTTP 비정상 응답 | `RemoteRequestException` | `AppRequestException` |
| Timeout, DNS, 연결 실패, 기타 I/O | `RemoteNetworkException` | `AppNetworkException` |
| 응답 직렬화 실패 | `RemoteDecodingException` | `AppDecodingException` |

```text
Retrofit / Kotlin Serialization 예외
  -> remoteDataCall
RemoteDataException
  -> withRemoteErrorMapping
AppException
  -> ViewModel
```

두 함수는 같은 변환을 반복하지 않습니다.

- `remoteDataCall`: 외부 라이브러리 오류를 Remote 계층 오류로 변환
- `withRemoteErrorMapping`: Remote 계층 오류를 Domain 계층 오류로 변환

이 두 경계를 합치면 RemoteDataSource가 Domain에 의존하거나 Repository가 Retrofit과 Serialization 구현 타입을 알아야 하므로 현재 모듈 구조에서는 분리합니다.

`RemoteNetworkException`과 `AppNetworkException`은 다음 reason을 보존합니다.

- `TIMEOUT`: 요청 시간 초과
- `CONNECTION`: DNS 조회, 연결 또는 route 실패
- `UNKNOWN`: 그 밖의 transport I/O 실패

`core:data:repository`의 `withRemoteErrorMapping`이 Remote 오류를 Domain 오류로 공통 변환하므로 feature는 Retrofit과 Remote 구현 타입에 의존하지 않습니다. 각 Repository는 예외별 `catch`를 작성하지 않고 remote 호출과 그 결과를 저장하는 블록만 이 함수로 감쌉니다.

```kotlin
override suspend fun refreshSamples() =
    withRemoteErrorMapping {
        sampleDao.replaceAll(remote.fetchSamples().map { it.toEntity() })
    }
```

이 함수는 `RemoteDataException`만 변환합니다. `CancellationException`과 Repository 내부의 예상하지 못한 예외는 잡지 않고 그대로 전파합니다.

## 서버 오류 코드

`AppRequestException`은 다음 정보를 보존합니다.

- `statusCode`: HTTP 상태
- `errorCode`: 알려진 서버 코드를 변환한 `AppErrorCode`
- `rawErrorCode`: 아직 앱이 모르는 원본 서버 코드

알 수 없는 코드는 `AppErrorCode.UNKNOWN`으로 처리하되 `rawErrorCode`를 잃지 않습니다. 닉네임 중복처럼 입력 상태나 dialog를 바꿔야 하는 코드는 feature가 직접 처리하고, 토큰 만료나 일반 서버 오류는 공통 정책으로 전달합니다.

## ViewModel 처리

```kotlin
suspendRunCatching {
    refreshSamples()
}.fold(
    onSuccess = {
        reduce { copy(isLoading = false) }
    },
    onFailure = { error ->
        reduce { copy(isLoading = false) }
        errorHandler.handle(error)
    },
)
```

화면 전용 오류는 `onFailure`에서 `AppRequestException.errorCode`를 확인해 UI state로 반영합니다. 화면이 처리하지 않은 오류만 `ErrorHandler.handle(error)`로 전달합니다.

## 전역 처리

`ErrorHandler`는 처리되지 않은 오류를 buffered channel로 전달하고 `AppViewModel`이 한 곳에서 수집합니다.

- `AppNetworkException`: timeout 또는 연결 안내
- `AppRequestException`: `AppErrorCode` 기반 공통 정책
- `AppDecodingException`: 응답 처리 오류
- 그 외: 알 수 없는 오류

인증 기능을 추가하면 토큰 만료 코드에서 세션을 삭제하고 로그인 back stack으로 교체합니다. 현재 템플릿은 처리 위치만 주석으로 제공하고 로그인 이동을 미리 구현하지 않습니다.

## 코루틴 예외

`suspendRunCatching`은 `CancellationException`을 다시 던집니다. `remoteDataCall`도 cancellation을 `IOException`이나 앱 실패로 변환하지 않습니다.

`CoroutineExceptionHandler`는 비즈니스 실패 복구에 사용하지 않습니다. 필요한 경우 처리되지 않은 root coroutine 예외의 로깅과 crash reporting을 위한 마지막 안전망으로만 사용합니다.

Feature의 기본 ViewModel 규칙은 [Feature Guide](FEATURE_GUIDE.md)를 참고합니다.
