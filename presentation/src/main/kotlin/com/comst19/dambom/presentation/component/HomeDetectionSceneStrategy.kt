package com.comst19.dambom.presentation.component

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategy
import com.comst19.dambom.core.navigation.contract.HOME_DETECTION_DESTINATION
import com.comst19.dambom.core.navigation.contract.HomeGraph.DetectionResultKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun homeDetectionSceneStrategy(
    directive: PaneScaffoldDirective,
    isResultPaneVisible: Boolean,
    libraryStrategy: SceneStrategy<NavKey>,
): SceneStrategy<NavKey> {
    val expansionState = rememberPaneExpansionState()
    val homeStrategy =
        rememberListDetailSceneStrategy<NavKey>(
            directive = directive,
            paneExpansionState = expansionState,
            backNavigationBehavior = BackNavigationBehavior.PopLatest,
        )
    LaunchedEffect(isResultPaneVisible) {
        if (isResultPaneVisible) expansionState.clear() else expansionState.setFirstPaneProportion(1f)
    }
    return SceneStrategy { sceneEntries ->
        val homeKeys = sceneEntries.map { it.metadata[HOME_DETECTION_DESTINATION] as? NavKey }
        when {
            usesHomeDetectionScene(homeKeys) -> with(homeStrategy) { calculateScene(sceneEntries) }
            homeKeys.lastOrNull() is DetectionResultKey -> null
            else -> with(libraryStrategy) { calculateScene(sceneEntries) }
        }
    }
}

internal fun usesHomeDetectionScene(keys: List<NavKey?>): Boolean =
    keys.lastOrNull() == HomeKey ||
        (keys.lastOrNull() is DetectionResultKey && keys.getOrNull(keys.lastIndex - 1) == HomeKey)
