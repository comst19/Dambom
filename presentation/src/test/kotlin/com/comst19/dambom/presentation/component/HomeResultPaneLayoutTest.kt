package com.comst19.dambom.presentation.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.navigation.Navigator
import com.comst19.dambom.core.navigation.contract.HOME_DETECTION_DESTINATION
import com.comst19.dambom.core.navigation.contract.HomeGraph.DetectionResultKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import com.comst19.dambom.core.navigation.rememberNavigationState
import com.comst19.dambom.presentation.navigation.AppNavigationConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1200dp-h800dp")
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class HomeResultPaneLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `result toggle changes pane visibility without changing the navigation entries`() {
        val visible = mutableStateOf(true)
        val resultKey = DetectionResultKey("https://example.com/video")
        val entries =
            listOf(
                NavEntry<NavKey>(
                    HomeKey,
                    metadata =
                        ListDetailSceneStrategy.listPane(sceneKey = HomeKey) +
                            mapOf(HOME_DETECTION_DESTINATION to HomeKey),
                ) { Text("Input", Modifier.fillMaxSize().testTag("input")) },
                NavEntry<NavKey>(
                    resultKey,
                    metadata =
                        ListDetailSceneStrategy.detailPane(sceneKey = HomeKey) +
                            mapOf(HOME_DETECTION_DESTINATION to resultKey),
                ) { Text("Selected result", Modifier.fillMaxSize().testTag("result")) },
            )
        composeRule.setContent {
            MaterialTheme {
                AppNavDisplay(
                    entries = entries,
                    navigator = Navigator(rememberNavigationState(AppNavigationConfig.navigation(HomeKey))),
                    isLibraryDetailPaneVisible = false,
                    isHomeResultPaneVisible = visible.value,
                    isVideoFullscreen = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeRule.onNodeWithTag("input").assertIsDisplayed()
        composeRule.onNodeWithTag("result").assertIsDisplayed()
        composeRule.runOnIdle { visible.value = false }
        composeRule.onNodeWithTag("input").assertIsDisplayed()
        composeRule.onNodeWithTag("result").assertIsNotDisplayed()
        composeRule.runOnIdle { visible.value = true }
        composeRule.onNodeWithTag("result").assertIsDisplayed()
    }
}
