# Database

## 모듈 소유권

`core:database`는 앱 Room 스키마의 단일 소유자입니다.

```text
core:database
├── AppDatabase
├── SampleEntity
├── SampleDao
├── DatabaseModule
└── migration / exported schema
```

`core:data:repository`는 `core:database`에 단방향으로 의존하고 DAO를 주입받습니다.

```text
core:data:repository
        |
        v
core:database
```

`core:database`가 Repository나 별도 local 모듈에 의존하지 않으므로 Gradle 순환 의존성이 생기지 않습니다.

## 참고 구조

- [Now in Android](https://github.com/android/nowinandroid)는 `core:database`가 Room database, Entity와 DAO를 소유하고 `core:data` Repository가 DAO를 직접 주입받습니다.
- [Pokedex](https://github.com/skydoves/Pokedex)는 `core-database`가 Database, Entity와 DAO를 소유하고 `core-data` Repository가 DAO를 직접 사용합니다.

Dambom도 같은 방향을 사용합니다. Room schema가 `core:database`에 위치하는 것은 인프라 누수가 아니라 해당 모듈의 명시적인 책임입니다. Room 타입은 Data 구현 내부에서만 사용하고 Domain과 Feature에는 노출하지 않습니다.

## 모델 경계

```text
SampleResponse
  -> NetworkSample
  -> SampleEntity
  -> Domain Sample
  -> SampleUiModel
```

- `SampleResponse`: 서버 DTO
- `NetworkSample`: RemoteDataSource가 Repository에 전달하는 모델
- `SampleEntity`: Room table schema
- Domain `Sample`: Repository 공개 계약
- `SampleUiModel`: 화면 표시 모델

Network model과 Entity 사이, Entity와 Domain 사이의 mapper는 둘을 조합하는 `core:data:repository`가 소유합니다.

```kotlin
internal fun NetworkSample.toEntity(nowEpochMillis: Long): SampleEntity =
    SampleEntity(
        id = id,
        title = title.trim(),
        description = description,
        syncedAtEpochMillis = nowEpochMillis,
    )

internal fun SampleEntity.toDomain(): Sample =
    Sample(
        id = id,
        title = title,
        description = description,
    )
```

## Repository와 DAO

Repository는 DAO의 Flow를 Domain model로 변환하고 remote 결과를 Entity로 저장합니다.

```kotlin
class DefaultSampleRepository @Inject constructor(
    private val remote: SampleRemoteDataSource,
    private val sampleDao: SampleDao,
) : SampleRepository {
    override fun observeSamples(): Flow<List<Sample>> =
        sampleDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun refreshSamples() =
        withRemoteErrorMapping {
            val now = System.currentTimeMillis()
            sampleDao.replaceAll(
                remote.fetchSamples().map { it.toEntity(now) },
            )
        }
}
```

Repository의 public interface는 Domain model만 반환합니다. ViewModel과 Feature는 `SampleDao`와 `SampleEntity`를 참조하지 않습니다.

## 트랜잭션 소유권

여러 SQL 작업이 하나의 저장 작업을 구성하면 DAO가 `@Transaction`으로 원자성을 보장합니다.

```kotlin
@Dao
interface SampleDao {
    @Query("DELETE FROM samples")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsertAll(samples: List<SampleEntity>)

    @Transaction
    suspend fun replaceAll(samples: List<SampleEntity>) {
        deleteAll()
        upsertAll(samples)
    }
}
```

Repository가 `AppDatabase.withTransaction`을 직접 호출하지 않으므로 SQL 작업의 원자성은 database 모듈 안에 남습니다. `replaceAll()` 관찰 테스트는 delete와 insert 사이의 빈 중간 상태가 외부로 방출되지 않는지 확인합니다.

## 트레이드오프

중앙 `core:database` 구조의 장점은 다음과 같습니다.

- Entity, DAO와 `RoomDatabase`가 한 방향으로만 참조되어 Gradle 순환 의존성이 없음
- migration과 exported schema를 한 모듈에서 관리
- 단일 DAO를 다시 감싸는 pass-through local 계층이 없음
- Repository 테스트에서는 DAO 계약만 대체하면 됨

비용도 있습니다.

- 어느 기능의 Entity가 바뀌어도 `core:database`와 이를 의존하는 Data 모듈이 다시 빌드됨
- 기능별 팀이 자신의 schema를 완전히 독립적으로 소유하기 어려움
- Repository 구현은 Room DAO와 Entity를 컴파일 의존성으로 알게 됨

Dambom은 하나의 앱과 하나의 Room database를 만드는 템플릿이므로 이 비용보다 단순한 단방향 구조의 이점이 큽니다. 서로 독립적인 database가 여러 개 생기거나 기능별 빌드·소유권 격리가 실제 요구사항이 되면 그때 database 모듈 분리를 검토합니다.

## 별도 LocalDataSource를 추가하는 경우

Room과 DataStore는 모두 local storage지만 기본적으로 서로 대체하는 같은 API가 아닙니다.

```text
SampleRepository   -> SampleDao           -> Room
SettingsRepository -> SettingsDataSource  -> DataStore
```

Room에서는 `@Dao` interface가 이미 SQL 구현을 숨기는 데이터별 local 계약이므로 Repository가 직접 주입받습니다.

```kotlin
class DefaultSampleRepository @Inject constructor(
    private val sampleDao: SampleDao,
) : SampleRepository {
    override fun observeSamples(): Flow<List<Sample>> =
        sampleDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
}
```

Preferences DataStore는 앱 데이터의 의미를 모르는 범용 key-value API이므로 `SettingsDataSource`가 설정별 계약과 기본값을 캡슐화합니다.

```kotlin
interface SettingsDataSource {
    val darkTheme: Flow<Boolean>
    suspend fun setDarkTheme(enabled: Boolean)
}

class PreferencesSettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsDataSource {
    override val darkTheme: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[DARK_THEME] ?: false
        }

    override suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[DARK_THEME] = enabled }
    }
}
```

- Room: 목록, 관계, 부분 조회, 정렬, transaction이 필요한 구조화 데이터
- DataStore: 설정, 플래그, 작은 key-value 또는 단일 typed object

`LocalDataSource`라는 범용 interface 하나에 Room과 DataStore를 모두 끼워 넣지 않습니다. 데이터 종류별로 `SampleLocalDataSource`, `SettingsDataSource`처럼 의미 있는 계약을 정의하며, 저장 기술은 그 계약의 구현 세부사항입니다.

예를 들어 같은 Settings 데이터를 DataStore와 Room 중 하나로 실제 교체해야 한다면 두 구현이 동일한 `SettingsDataSource` 계약을 구현할 수 있습니다. 반면 Sample 목록과 앱 설정은 데이터와 연산 의미가 다르므로 같은 local interface를 공유하지 않습니다.

다음 중 하나가 실제로 필요할 때만 local data source 계약을 도입합니다.

- Room과 파일 저장소처럼 교체 가능한 local 구현이 둘 이상 존재
- 여러 DAO를 조합하는 독립적인 local 정책을 Repository와 분리해야 함
- database 모듈을 별도 제품이나 SDK에서 재사용하며 Room API를 숨겨야 함

단일 Room DAO만 전달하는 `RoomSampleLocalDataSource`와 DAO를 그대로 복제한 interface는 추가하지 않습니다. 추상화가 필요해지면 소비자인 Data 계층이 계약을 소유하고 Room 구현이 그 계약을 구현하도록 의존 방향을 다시 설계합니다.

## 피해야 할 구조

```text
core:database -> core:data:local
core:data:local -> core:database
```

Entity와 DAO를 local 모듈에 두면서 `AppDatabase`만 database 모듈에 두면 양쪽이 서로의 타입을 필요로 하게 됩니다. Entity, DAO와 `RoomDatabase`는 같은 schema 소유 모듈에 둡니다.

또한 다음을 피합니다.

- Domain model에 `@Entity`를 붙임
- Feature나 ViewModel이 DAO를 직접 사용
- Repository public API로 Entity를 반환
- 단일 DAO를 위한 pass-through local interface를 미리 생성
- 여러 쓰기 작업을 Repository에서 transaction 없이 순서대로 호출
