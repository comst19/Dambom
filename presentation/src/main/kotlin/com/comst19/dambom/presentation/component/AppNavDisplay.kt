package com.comst19.dambom.presentation.component

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.comst19.dambom.core.navigation.Navigator

/**
 * decorate된 [entries]를 Navigation 3 [NavDisplay]에 연결하고 Back 및 공통 화면 전환을 설정합니다.
 * 일반 Back과 predictive Back은 [navigator]에 위임하므로 개별 화면에서 BackHandler를 중복 등록하지 않습니다.
 */
@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun AppNavDisplay(
    entries: List<NavEntry<NavKey>>,
    navigator: Navigator,
    modifier: Modifier,
) {
    val listDetailSceneStrategy = rememberListDetailSceneStrategy<NavKey>()
    NavDisplay(
        entries = entries,
        onBack = navigator::goBack,
        sceneStrategies = listOf(listDetailSceneStrategy),
        // Navigation 3 기본 scale 전환 대신 화면 크기가 유지되는 fade 전환을 사용합니다.
        transitionSpec = {
            fadeIn(tween(NAVIGATION_FADE_DURATION_MILLIS)) togetherWith
                fadeOut(tween(NAVIGATION_FADE_DURATION_MILLIS))
        },
        popTransitionSpec = {
            fadeIn(tween(NAVIGATION_FADE_DURATION_MILLIS)) togetherWith
                fadeOut(tween(NAVIGATION_FADE_DURATION_MILLIS))
        },
        predictivePopTransitionSpec = {
            fadeIn(tween(NAVIGATION_FADE_DURATION_MILLIS)) togetherWith
                fadeOut(tween(NAVIGATION_FADE_DURATION_MILLIS))
        },
        modifier = modifier,
    )
}

private const val NAVIGATION_FADE_DURATION_MILLIS = 700
