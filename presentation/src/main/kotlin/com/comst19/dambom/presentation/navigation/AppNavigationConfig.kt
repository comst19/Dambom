package com.comst19.dambom.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.comst19.dambom.core.navigation.NavigationConfig
import com.comst19.dambom.core.navigation.TopLevelBackBehavior
import com.comst19.dambom.core.navigation.TopLevelNavKey
import com.comst19.dambom.feature.detection.navigation.detectionNavSerializersModule
import com.comst19.dambom.feature.downloads.navigation.downloadsNavSerializersModule
import com.comst19.dambom.feature.home.api.HomeKey
import com.comst19.dambom.feature.home.navigation.homeNavSerializersModule
import com.comst19.dambom.feature.library.api.LibraryKey
import com.comst19.dambom.feature.library.navigation.libraryNavSerializersModule
import com.comst19.dambom.feature.settings.navigation.settingsNavSerializersModule
import com.comst19.dambom.feature.web.navigation.webNavSerializersModule
import com.comst19.dambom.presentation.R
import kotlinx.serialization.modules.SerializersModule

/** 독립 top-level key와 선택적인 바텀바 표시 정보를 함께 보관하는 앱 설정입니다. */
@Immutable
internal data class AppTopLevelDestination(
    val key: TopLevelNavKey,
    @StringRes val bottomBarLabelRes: Int? = null,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null,
)

/** 앱의 시작 destination, top-level Back 정책과 바텀바 항목을 정의하는 단일 설정 원본입니다. */
object AppNavigationConfig {
    /** 각 feature가 소유한 모든 Navigation 3 key serializer를 조합한 앱 전역 모듈입니다. */
    val serializersModule: SerializersModule =
        SerializersModule {
            include(homeNavSerializersModule)
            include(detectionNavSerializersModule)
            include(webNavSerializersModule)
            include(downloadsNavSerializersModule)
            include(libraryNavSerializersModule)
            include(settingsNavSerializersModule)
        }

    /** 모든 독립 stack을 등록하며 `bottomBarLabelRes`가 있는 항목만 바텀바에 표시합니다. */
    internal val topLevelDestinations =
        listOf(
            AppTopLevelDestination(
                key = HomeKey,
                bottomBarLabelRes = R.string.destination_home,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
            ),
            AppTopLevelDestination(
                key = LibraryKey,
                bottomBarLabelRes = R.string.destination_library,
                selectedIcon = Icons.Filled.VideoLibrary,
                unselectedIcon = Icons.Outlined.VideoLibrary,
            ),
        )

    /** [topLevelDestinations]에서 파생해 NavigationState가 생성할 독립 stack key를 제공합니다. */
    private val topLevelKeys: Set<TopLevelNavKey> = topLevelDestinations.mapTo(linkedSetOf()) { it.key }

    /** [topLevelDestinations]에서 파생한 바텀바 노출 대상이며 화면 chrome 정책에서 사용합니다. */
    internal val bottomBarKeys: Set<TopLevelNavKey> =
        topLevelDestinations
            .filter { it.bottomBarLabelRes != null }
            .mapTo(linkedSetOf()) { it.key }

    /** 앱의 시작 destination과 top-level Back 정책을 core navigation에 주입합니다. */
    fun navigation(startKey: TopLevelNavKey) =
        NavigationConfig(
            startKey = startKey,
            bottomHomeKey = HomeKey,
            topLevelKeys = topLevelKeys,
            bottomBarKeys = bottomBarKeys,
            topLevelBackBehavior = TopLevelBackBehavior.ExitThroughHome,
        )
}
