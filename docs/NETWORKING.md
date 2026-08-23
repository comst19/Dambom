# Networking

## 역할

- `core:network`: OkHttp, Retrofit, JSON, 공통 header와 interceptor, CallAdapter
- `core:data:remote`: 서비스 API, DTO, network model, mapper와 RemoteDataSource
- `core:data:repository`: remote/local 결과 조합과 Domain 경계 변환

Feature와 Domain은 Retrofit, OkHttp, DTO와 Remote 예외를 직접 참조하지 않습니다. 전체 의존성과 데이터 흐름은 [Architecture](ARCHITECTURE.md)를 참고합니다.

## Build type 설정

`app`의 `BuildConfig`가 API base URL과 `APP_ENVIRONMENT`를 정의합니다. `AppConfigModule`이 이를 `AppEnvironment`와 `NetworkConfig`로 변환해 주입하므로 feature와 core 모듈은 `BuildConfig`를 직접 읽지 않습니다. `debug`, `qa`는 `BASIC`, `release`는 `NONE` 로그 수준을 사용하며, 템플릿의 `example.invalid` URL은 실제 환경 URL로 교체해야 합니다.

## 패키지 구조

```text
core:network
├── calladapter/
├── di/
├── header/
├── interceptor/
└── model/

core:data:remote
├── api/
├── datasource/
├── di/
├── error/
├── mapper/
└── model/
```

Gradle 모듈이 의존성 경계를 만들고 패키지는 한 모듈 안의 탐색 경계를 만듭니다. 서비스별 API와 DTO는 `core:data:remote`, 여러 서비스가 재사용하는 HTTP 기반은 `core:network`에 둡니다.

## 응답 흐름

```text
SampleApi
  -> ApiResponse<SampleResponse>
  -> unwrapData()
  -> NetworkSample
  -> Repository
```

Retrofit API는 서버 envelope를 나타내는 `ApiResponse<T>`를 반환합니다. RemoteDataSource가 `unwrapData()`로 envelope를 제거하고 DTO를 network model로 변환합니다. Repository에는 DTO를 노출하지 않습니다.

## CallAdapter

`NetworkCallAdapterFactory`는 Retrofit에 한 번 등록합니다. 성공 응답은 converter에 전달하고 비-2xx 오류 body는 `NetworkHttpException(statusCode, errorCode, message)`으로 변환합니다. 각 API나 RemoteDataSource가 오류 body를 반복해서 파싱하지 않습니다.

Custom CallAdapter는 Retrofit 사용의 필수 요소가 아닙니다. 서버 오류 envelope를 공통으로 파싱하고 HTTP 오류 계약을 중앙화할 때 사용합니다.

## RemoteDataSource

RemoteDataSource는 API 호출, envelope 제거와 network model 변환만 담당합니다. `remoteDataCall`은 외부 라이브러리 예외를 Remote 계층 오류로 바꿉니다.

- 비-2xx HTTP 응답: `RemoteRequestException`
- transport `IOException`: `RemoteNetworkException`
- 직렬화 실패: `RemoteDecodingException`

```kotlin
override suspend fun fetchSamples(): List<NetworkSample> =
    remoteDataCall {
        api.getSamples()
            .unwrapData()
            .map(SampleResponse::toNetworkSample)
    }
```

`remoteDataCall`은 Retrofit과 Kotlin Serialization에 종속된 오류를 Remote 계약으로 정규화하는 첫 번째 경계입니다. Domain 오류로 직접 변환하지 않으므로 `core:data:remote`가 `core:domain`에 의존하지 않습니다.

오류 타입과 상위 계층 처리 기준은 [Error Handling](ERROR_HANDLING.md)을 참고합니다.
