# Edge-to-edge

## 기본 원칙

- Activity는 `setContent` 전에 `enableEdgeToEdge()`를 호출합니다.
- 앱 `Scaffold`는 `WindowInsets.safeDrawing`으로 계산한 `innerPadding`을 전달만 합니다.
- 각 화면은 일반, 스크롤, 전체 화면 특성에 맞게 inset을 한 번만 소비합니다.
- 상태 바와 gesture navigation bar는 투명하게 유지하고 `Scaffold`, Material component 또는 전체 화면 콘텐츠가 뒤의 배경을 그립니다.
- 시스템 바 아이콘 명암은 실제로 뒤에 그려진 배경색과 맞춥니다.
- `window.statusBarColor`로 edge-to-edge를 되돌리지 않습니다.

현재 진입점과 공통 구현은 `MainActivity`, `AppScaffold`, `AppChrome`, `LocalAppScaffoldPadding`입니다.

## 일반 화면

고정 콘텐츠는 앱 `Scaffold`가 전달한 패딩을 modifier에 적용하고 소비합니다.

```kotlin
Column(
    modifier =
        Modifier
            .fillMaxSize()
            .appScaffoldPadding()
            .padding(24.dp),
) {
    // Content
}
```

`appScaffoldPadding()`은 `padding()`과 `consumeWindowInsets()`를 함께 적용합니다. 하위에서 같은 inset을 다시 적용하지 않습니다.

## 스크롤 화면

`LazyColumn`에는 부모 modifier 패딩 대신 `contentPadding`을 사용합니다. 그래야 항목이 시스템 바 뒤로 스크롤되면서 첫 항목과 마지막 항목은 안전 영역에 배치됩니다.

```kotlin
val appPadding = LocalAppScaffoldPadding.current

LazyColumn(
    modifier =
        Modifier
            .fillMaxSize()
            .consumeWindowInsets(appPadding),
    contentPadding = appPadding,
) {
    items(items) { item ->
        Item(item)
    }
}
```

## TopAppBar와 다른 본문 색

상단 앱 바가 있다면 Material 3 `TopAppBar`가 상태 바 뒤까지 이어지는 배경과 inset을 소유하게 합니다.

```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("Title") },
        )
    },
) { innerPadding ->
    ScreenContent(
        modifier =
            Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
    )
}
```

`TopAppBar`의 부모에 상태 바 패딩을 적용하지 않고 Material 3 app bar의 기본 `windowInsets` 처리를 사용합니다. app bar의 `containerColor`를 바꾸면 상태 바 뒤에 보이는 색도 함께 바뀝니다.

## 전체 화면

이미지, 상세 화면과 같은 전체 화면은 배경을 시스템 바 뒤까지 그리고 버튼과 텍스트 같은 조작 영역만 보호합니다.

```kotlin
Box(
    modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.sampleDetailBackground),
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
    ) {
        // Interactive content
    }
}
```

배경 Box에 `safeDrawingPadding()`을 적용하면 배경까지 시스템 바 아래에서 잘리므로 조작 영역에만 적용합니다.

## 이미지와 상태 바 보호막

이미지는 위치마다 명도가 달라 시스템 바 아이콘 대비를 보장하기 어렵습니다. 필요한 화면에서만 `StatusBarProtection` gradient를 마지막에 겹쳐 그립니다.

```kotlin
Box(Modifier.fillMaxSize()) {
    Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )

    StatusBarProtection(
        color = Color.Black,
        modifier = Modifier.align(Alignment.TopCenter),
    )
}
```

단색 배경의 일반 화면에는 보호막을 추가하지 않습니다.

## 시스템 바 아이콘 명암

`AppChrome`은 현재 경로에서 실제로 시스템 바 뒤에 보이는 색을 기준으로 아이콘 명암을 결정합니다.

일반 화면은 `surface`, 전체 화면은 실제 전체 화면 배경을 기준으로 명암을 계산합니다.

```kotlin
val statusBarBackground =
    if (isFullScreen) {
        MaterialTheme.colorScheme.sampleDetailBackground
    } else {
        MaterialTheme.colorScheme.surface
    }
```

```kotlin
val useDarkStatusBarIcons = statusBarBackground.luminance() > 0.5f
```

이미지처럼 단일 색으로 계산할 수 없는 화면이 생기면 그 Route의 아이콘 모드만 `AppUiPolicy` 예외로 추가합니다. 별도 상태 바 색 Map은 만들지 않습니다.

전체 화면의 배경과 아이콘 명암 계산은 `sampleDetailBackground`처럼 화면 의미를 가진 design system 토큰을 공유합니다. 색을 변경할 때는 토큰 한 곳만 수정합니다.

현재 샘플은 화면 안의 설명 문구와 함께 다음 세 방식을 각각 보여줍니다.

- `SettingsScreen`: `TopAppBar` 배경이 투명 상태 바 뒤까지 이어집니다.
- `SampleScreen`: 기본 `Scaffold` 배경이 투명 상태 바 뒤에 보입니다.
- `SampleDetailScreen`: 전체 배경이 양쪽 시스템 바 뒤까지 이어지고 조작 영역만 `safeDrawingPadding()`으로 보호됩니다.

Feature Screen은 상태 바 전용 색을 확인하거나 전달하지 않습니다.

## 하단 NavigationBar

Material 3 `NavigationBar`가 navigation bar inset과 배경을 소유하게 합니다. `MainActivity`는 API 29 이상에서 `isNavigationBarContrastEnforced = false`로 시스템의 추가 scrim을 막습니다.

```kotlin
NavigationBar(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
) {
    // Items
}
```

전체 화면에서 bottom bar가 없다면 화면 배경이 navigation bar 뒤까지 이어집니다.

## 선택 기준

| 화면 | 적용 방식 |
|---|---|
| 일반 고정 화면 | `Modifier.appScaffoldPadding()` |
| `LazyColumn`/`LazyRow` | `contentPadding`과 `consumeWindowInsets` |
| `TopAppBar` 화면 | Material app bar의 `windowInsets`와 `containerColor` |
| 전체 화면 | 배경은 그대로, 조작 영역에 `safeDrawingPadding()` |
| 이미지/영상 | 필요한 경우에만 `StatusBarProtection` |

## 피해야 할 패턴

- 화면 루트와 자식에서 같은 inset을 중복 적용
- `LazyColumn` 부모에 `Modifier.padding(innerPadding)`을 적용해 스크롤 영역을 자름
- 전체 화면 배경 Box에 `safeDrawingPadding()`을 적용
- `TopAppBar` 부모를 상태 바만큼 내림
- 상태 바 전용 색 Map과 전역 overlay를 불필요하게 추가
- 화면이 실제로 그리는 색과 다른 색으로 시스템 바 아이콘 명암을 계산

공식 기준은 [Set up Edge-to-edge](https://developer.android.com/develop/ui/compose/system/setup-e2e), [Use Material 3 insets](https://developer.android.com/develop/ui/compose/system/material-insets), [Set up window insets](https://developer.android.com/develop/ui/compose/system/insets-ui), [About system bar protection](https://developer.android.com/develop/ui/compose/system/system-bars)을 참고합니다.
