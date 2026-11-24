package com.comst19.dambom.presentation.navigation

import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.serialization.encodeToSavedState
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.domain.model.NetworkConnection
import com.comst19.dambom.core.navigation.ChannelNavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationConfig
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.NavigationState
import com.comst19.dambom.core.navigation.Navigator
import com.comst19.dambom.core.navigation.navigationStateSaver
import com.comst19.dambom.core.navigation.rememberNavigationState
import com.comst19.dambom.feature.detection.api.DetectionResultKey
import com.comst19.dambom.feature.downloads.api.DownloadsKey
import com.comst19.dambom.feature.home.api.HomeKey
import com.comst19.dambom.feature.library.api.LibraryKey
import com.comst19.dambom.feature.library.api.VideoDetailKey
import com.comst19.dambom.feature.settings.api.SettingsKey
import com.comst19.dambom.feature.web.api.WebKey
import com.comst19.dambom.presentation.DambomApp
import com.comst19.dambom.presentation.DambomAppEntryContext
import com.comst19.dambom.presentation.LocalDambomEntryProviderFactory
import com.comst19.dambom.presentation.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppNavigationRestorationTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val config = AppNavigationConfig.navigation(HomeKey)

    @Test
    fun `selected tab per tab history and distinct key arguments survive recreation without resetting to home`() {
        val webKey = WebKey(url = "https://web.example.com/browse?tab=1", sharedLink = false)
        val detectionKey =
            DetectionResultKey(
                url = "https://detect.example.com/watch?v=42",
                snapshotId = "snap-42",
                requestId = "req-99",
                sharedLink = true,
            )
        val detailKey = VideoDetailKey(id = "video-77", isPrivate = true)

        val restoration = StateRestorationTester(composeRule)
        lateinit var state: NavigationState
        restoration.setContent {
            state = rememberNavigationState(config = config, serializersModule = AppNavigationConfig.serializersModule)
        }

        composeRule.runOnIdle {
            Navigator(state).apply {
                navigate(webKey)
                navigate(detectionKey)
                navigate(DownloadsKey)
                navigate(SettingsKey)
                navigateTopLevel(LibraryKey)
                navigate(detailKey)
            }
        }

        val saver = navigationStateSaver(config = config, serializersModule = AppNavigationConfig.serializersModule)
        val savedBundle = with(saver) { SaverScope { true }.save(state) as Bundle }
        assertStructuredSnapshotBundle(savedBundle, webKey, detectionKey, detailKey)

        val parcelRestored = requireNotNull(saver.restore(roundTripParcel(savedBundle)))
        assertRestoredFields(parcelRestored, webKey, detectionKey, detailKey)

        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertRestoredFields(state, webKey, detectionKey, detailKey)
        }
    }

    @Test
    fun `legacy upgrade in real DambomApp preserves panels resets fullscreen on home and persists second navigation`() {
        val harness = mountDambomAppHarness(config)
        harness.seedNonDefaultPanelsAndFullscreen(navigateToLibraryFirst = true)
        val seedSaved = harness.save()
        val newNavSlotKey = findNavigationSnapshotKey(seedSaved)
        val legacySaved = buildLegacyUpgradeMap(seedSaved, newNavSlotKey)

        harness.fullscreenCallbacks.clear()
        harness.restore(legacySaved)

        val firstCtx = harness.context()
        assertEquals(HomeKey, firstCtx.state.currentTopLevel)
        assertEquals(HomeKey, firstCtx.state.currentKey)
        assertEquals(listOf(HomeKey), firstCtx.state.topLevelsInUse)
        assertFalse(firstCtx.panes.isLibraryDetailPaneVisible())
        assertFalse(firstCtx.panes.isHomeResultPaneVisible())
        assertFalse(firstCtx.isVideoFullscreen())
        assertTrue(harness.fullscreenCallbacks.contains(false))
        assertHomeTabsAndRootBackActive()

        harness.dispatch(
            NavigationEvent.NavigateTopLevel(LibraryKey),
            NavigationEvent.Navigate(VideoDetailKey(id = "after-upgrade-video", isPrivate = true)),
        )
        val secondSaved = harness.save()
        assertTrue(newNavSlotKey in secondSaved)

        harness.restore(secondSaved)
        val secondCtx = harness.context()
        assertEquals(LibraryKey, secondCtx.state.currentTopLevel)
        assertEquals(listOf(HomeKey, LibraryKey), secondCtx.state.topLevelsInUse)
        val restoredDetail = secondCtx.state.currentKey as VideoDetailKey
        assertEquals("after-upgrade-video", restoredDetail.id)
        assertTrue(restoredDetail.isPrivate)
        assertFalse(secondCtx.panes.isLibraryDetailPaneVisible())
        assertFalse(secondCtx.panes.isHomeResultPaneVisible())
        assertFalse(secondCtx.isVideoFullscreen())
    }

    @Test
    fun `legacy upgrade in real DambomApp starts from configured non home startKey and keeps panel states`() {
        val harness = mountDambomAppHarness(AppNavigationConfig.navigation(LibraryKey))
        harness.seedNonDefaultPanelsAndFullscreen(navigateToLibraryFirst = false)
        val seedSaved = harness.save()
        harness.restore(buildLegacyUpgradeMap(seedSaved, findNavigationSnapshotKey(seedSaved)))

        val ctx = harness.context()
        assertEquals(listOf(HomeKey, LibraryKey), ctx.state.topLevelsInUse)
        assertEquals(LibraryKey, ctx.state.currentTopLevel)
        assertEquals(LibraryKey, ctx.state.currentKey)
        assertFalse(ctx.panes.isLibraryDetailPaneVisible())
        assertFalse(ctx.panes.isHomeResultPaneVisible())
        assertFalse(ctx.isVideoFullscreen())
        assertBottomTabsDisplayed()
    }

    @Test
    fun `corrupted new app navigation snapshot resets all tabs atomically while keeping panel states`() {
        val harness = mountDambomAppHarness(config)
        composeRule.runOnIdle {
            harness.context().panes.onLibraryDetailPaneVisibilityChange(false)
            harness.context().panes.onHomeResultPaneVisibilityChange(false)
        }
        composeRule.waitForIdle()
        val savedValues = harness.save()
        val navSlotKey = findNavigationSnapshotKey(savedValues)

        corruptAppSnapshotPayloads().forEach { corruptValue ->
            val restoredMap = savedValues.toMutableMap()
            restoredMap[navSlotKey] = listOf(corruptValue)
            harness.restore(restoredMap)

            val ctx = harness.context()
            val backStacks = ctx.state.backStacks
            assertEquals(listOf(HomeKey), ctx.state.topLevelsInUse)
            assertEquals(HomeKey, ctx.state.currentTopLevel)
            assertEquals(listOf(HomeKey), backStacks.getValue(HomeKey).toList())
            assertEquals(listOf(LibraryKey), backStacks.getValue(LibraryKey).toList())
            assertFalse(ctx.panes.isLibraryDetailPaneVisible())
            assertFalse(ctx.panes.isHomeResultPaneVisible())
        }
    }

    @Test
    fun `already open video detail entry receives fullscreen toggle updates in same NavDisplay entry`() {
        val harness = mountDambomAppHarness(config)
        harness.dispatch(
            NavigationEvent.NavigateTopLevel(LibraryKey),
            NavigationEvent.Navigate(VideoDetailKey(id = "open-video-1", isPrivate = false)),
        )

        val initialEntryToken = requireNotNull(harness.activeVideoDetailEntryToken)
        composeRule.onNodeWithText("screen:detail:open-video-1:false:fullscreen=false").assertIsDisplayed()

        composeRule.runOnIdle {
            harness.context().onVideoFullscreenChange(true)
        }
        composeRule.waitForIdle()
        assertEquals(initialEntryToken, harness.activeVideoDetailEntryToken)
        composeRule.onNodeWithText("screen:detail:open-video-1:false:fullscreen=true").assertIsDisplayed()

        composeRule.runOnIdle {
            harness.context().onVideoFullscreenChange(false)
        }
        composeRule.waitForIdle()
        assertEquals(initialEntryToken, harness.activeVideoDetailEntryToken)
        composeRule.onNodeWithText("screen:detail:open-video-1:false:fullscreen=false").assertIsDisplayed()
    }

    private fun mountDambomAppHarness(navigationConfig: NavigationConfig): DambomAppHarness {
        val harness = DambomAppHarness()
        composeRule.setContent {
            if (harness.mounted) {
                DambomTheme(darkTheme = false) {
                    CompositionLocalProvider(
                        LocalSaveableStateRegistry provides harness.activeRegistry,
                        LocalDambomEntryProviderFactory provides { context ->
                            harness.observedContext = context
                            testEntries(context, harness)
                        },
                    ) {
                        DambomApp(
                            navigationConfig = navigationConfig,
                            dispatcher = harness.dispatcher,
                            appEventBus = harness.appEventBus,
                            networkAccess = NetworkAccessState(connection = NetworkConnection.UNMETERED),
                            onVideoFullscreenChange = harness.fullscreenCallbacks::add,
                            onVideoRotate = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return harness
    }

    private fun assertBottomTabsDisplayed() {
        val homeLabel = composeRule.activity.getString(R.string.destination_home)
        val libraryLabel = composeRule.activity.getString(R.string.destination_library)
        composeRule.onNodeWithText(homeLabel).assertIsDisplayed()
        composeRule.onNodeWithText(libraryLabel).assertIsDisplayed()
    }

    private fun assertHomeTabsAndRootBackActive() {
        assertBottomTabsDisplayed()
        val rootBackHint = composeRule.activity.getString(R.string.root_back_hint)
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(rootBackHint).assertIsDisplayed()
    }

    private fun corruptAppSnapshotPayloads(): List<Any> {
        val saver = navigationStateSaver(config = config, serializersModule = AppNavigationConfig.serializersModule)
        val seedState =
            NavigationState(
                bottomHomeKey = config.bottomHomeKey,
                bottomBarKeys = config.bottomBarKeys,
                topLevelHistory = NavBackStack<NavKey>(HomeKey, LibraryKey),
                backStacks =
                    mapOf(
                        HomeKey to NavBackStack<NavKey>(HomeKey, WebKey("https://example.com")),
                        LibraryKey to NavBackStack<NavKey>(LibraryKey, VideoDetailKey("vid-1", isPrivate = true)),
                    ),
                topLevelBackBehavior = config.topLevelBackBehavior,
            )
        val validBundle = with(saver) { SaverScope { true }.save(seedState) as Bundle }
        return listOf(
            replaceStringValue(validBundle, "dambom.video_detail", "o04"),
            replaceStringValue(
                validBundle,
                "dambom.home",
                "com.comst19.dambom.core.navigation.contract.HomeGraph\$HomeKey",
            ),
            replaceStringValue(validBundle, "dambom.web", "dambom.unknown"),
        )
    }

    private inner class DambomAppHarness {
        val dispatcher = ChannelNavigationDispatcher()
        val appEventBus = AppEventBus()
        val fullscreenCallbacks = mutableListOf<Boolean>()
        var activeRegistry by mutableStateOf(SaveableStateRegistry(restoredValues = null, canBeSaved = { true }))
        var mounted by mutableStateOf(true)
        var observedContext: DambomAppEntryContext? = null
        var activeVideoDetailEntryToken: Any? = null

        fun context(): DambomAppEntryContext = requireNotNull(observedContext)

        fun dispatch(vararg events: NavigationEvent) {
            composeRule.runOnIdle {
                runBlocking {
                    events.forEach { dispatcher.dispatch(it) }
                }
            }
            composeRule.waitForIdle()
        }

        fun seedNonDefaultPanelsAndFullscreen(navigateToLibraryFirst: Boolean) {
            if (navigateToLibraryFirst) {
                dispatch(
                    NavigationEvent.NavigateTopLevel(LibraryKey),
                    NavigationEvent.Navigate(VideoDetailKey("seed-video", isPrivate = true)),
                )
            } else {
                dispatch(NavigationEvent.Navigate(VideoDetailKey("seed-video", isPrivate = false)))
            }
            composeRule.runOnIdle {
                val ctx = context()
                ctx.panes.onLibraryDetailPaneVisibilityChange(false)
                ctx.panes.onHomeResultPaneVisibilityChange(false)
                ctx.onVideoFullscreenChange(true)
            }
            composeRule.waitForIdle()
        }

        fun save(): Map<String, List<Any?>> = activeRegistry.performSave()

        fun restore(savedValues: Map<String, List<Any?>>) {
            val nextRegistry = SaveableStateRegistry(restoredValues = savedValues, canBeSaved = { true })
            composeRule.runOnIdle { mounted = false }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                activeRegistry = nextRegistry
                mounted = true
            }
            composeRule.waitForIdle()
        }
    }

    private fun testEntries(
        context: DambomAppEntryContext,
        harness: DambomAppHarness,
    ): (NavKey) -> NavEntry<NavKey> =
        entryProvider<NavKey> {
            entry<HomeKey> { Text("screen:home") }
            entry<LibraryKey> { Text("screen:library") }
            entry<VideoDetailKey> { key ->
                val entryToken = remember { Any() }
                harness.activeVideoDetailEntryToken = entryToken
                Text("screen:detail:${key.id}:${key.isPrivate}:fullscreen=${context.isVideoFullscreen()}")
            }
            entry<WebKey> { key -> Text("screen:web:${key.url}") }
            entry<DetectionResultKey> { key -> Text("screen:detection:${key.url}") }
            entry<DownloadsKey> { Text("screen:downloads") }
            entry<SettingsKey> { Text("screen:settings") }
        }

    private fun buildLegacyUpgradeMap(
        seedSaved: Map<String, List<Any?>>,
        newNavSlotKey: String,
    ): Map<String, List<Any?>> {
        val legacyTopHistory =
            encodeToSavedState(
                serializer = NavBackStackSerializer(NavKeySerializer()),
                value = NavBackStack<NavKey>(HomeKey, LibraryKey),
            )
        val legacyHomeStack =
            encodeToSavedState(
                serializer = NavBackStackSerializer(NavKeySerializer()),
                value = NavBackStack<NavKey>(HomeKey, WebKey("https://legacy.example")),
            )
        val legacyLibraryStack =
            encodeToSavedState(
                serializer = NavBackStackSerializer(NavKeySerializer()),
                value = NavBackStack<NavKey>(LibraryKey, VideoDetailKey("legacy-vid", isPrivate = true)),
            )
        return buildMap {
            seedSaved.forEach { (key, value) ->
                if (key != newNavSlotKey) put(key, value)
            }
            put("legacy_top_history", listOf(legacyTopHistory))
            put("legacy_home_stack", listOf(legacyHomeStack))
            put("legacy_library_stack", listOf(legacyLibraryStack))
        }
    }

    private fun findNavigationSnapshotKey(saved: Map<String, List<Any?>>): String =
        saved.entries
            .single { (_, values) ->
                val bundle = values.singleOrNull() as? Bundle
                bundle != null && bundle.containsKey("topLevelHistory") && bundle.containsKey("backStacks")
            }.key

    private fun assertStructuredSnapshotBundle(
        savedBundle: Bundle,
        expectedWeb: WebKey,
        expectedDetection: DetectionResultKey,
        expectedDetail: VideoDetailKey,
    ) {
        val topLevelHistory =
            indexedBundles(requireNotNull(savedBundle.getBundle("topLevelHistory")))
                .map { it.getString("type") }
        assertEquals(listOf("dambom.home", "dambom.library"), topLevelHistory)

        val tabsByRoot =
            indexedBundles(requireNotNull(savedBundle.getBundle("backStacks"))).associate { tab ->
                val rootType = requireNotNull(tab.getBundle("rootKey")?.getString("type"))
                val stack = indexedBundles(requireNotNull(tab.getBundle("stack")))
                rootType to stack
            }

        val homeStack = requireNotNull(tabsByRoot["dambom.home"])
        assertEquals(
            listOf("dambom.home", "dambom.web", "dambom.detection_result", "dambom.downloads", "dambom.settings"),
            homeStack.map { it.getString("type") },
        )
        val webValue = requireNotNull(homeStack[1].getBundle("value"))
        assertEquals(expectedWeb.url, webValue.getString("url"))
        assertEquals(expectedWeb.sharedLink, webValue.getBoolean("sharedLink", false))

        val detectionValue = requireNotNull(homeStack[2].getBundle("value"))
        assertEquals(expectedDetection.url, detectionValue.getString("url"))
        assertEquals(expectedDetection.snapshotId, detectionValue.getString("snapshotId"))
        assertEquals(expectedDetection.requestId, detectionValue.getString("requestId"))
        assertEquals(expectedDetection.sharedLink, detectionValue.getBoolean("sharedLink", false))

        val libraryStack = requireNotNull(tabsByRoot["dambom.library"])
        assertEquals(listOf("dambom.library", "dambom.video_detail"), libraryStack.map { it.getString("type") })
        val detailValue = requireNotNull(libraryStack[1].getBundle("value"))
        assertEquals(expectedDetail.id, detailValue.getString("id"))
        assertEquals(expectedDetail.isPrivate, detailValue.getBoolean("isPrivate", false))
        assertFalse(collectBundleStrings(savedBundle).any { it.contains("com.comst19.dambom") })
    }

    private fun assertRestoredFields(
        state: NavigationState,
        expectedWeb: WebKey,
        expectedDetection: DetectionResultKey,
        expectedDetail: VideoDetailKey,
    ) {
        assertEquals(LibraryKey, state.currentTopLevel)
        assertEquals(listOf(HomeKey, LibraryKey), state.topLevelsInUse)

        val homeStack = state.backStacks.getValue(HomeKey).toList()
        assertEquals(5, homeStack.size)
        assertEquals(HomeKey, homeStack[0])
        val restoredWeb = homeStack[1] as WebKey
        assertEquals(expectedWeb.url, restoredWeb.url)
        assertEquals(expectedWeb.sharedLink, restoredWeb.sharedLink)
        val restoredDetection = homeStack[2] as DetectionResultKey
        assertEquals(expectedDetection.url, restoredDetection.url)
        assertEquals(expectedDetection.snapshotId, restoredDetection.snapshotId)
        assertEquals(expectedDetection.requestId, restoredDetection.requestId)
        assertEquals(expectedDetection.sharedLink, restoredDetection.sharedLink)
        assertEquals(DownloadsKey, homeStack[3])
        assertEquals(SettingsKey, homeStack[4])

        val libraryStack = state.backStacks.getValue(LibraryKey).toList()
        assertEquals(2, libraryStack.size)
        assertEquals(LibraryKey, libraryStack[0])
        val restoredDetail = libraryStack[1] as VideoDetailKey
        assertEquals(expectedDetail.id, restoredDetail.id)
        assertEquals(expectedDetail.isPrivate, restoredDetail.isPrivate)
        assertEquals(expectedDetail, state.currentKey)
        assertNotNull(restoredWeb.url)
    }

    private fun indexedBundles(bundle: Bundle): List<Bundle> =
        buildList {
            var index = 0
            while (bundle.containsKey(index.toString())) {
                add(requireNotNull(bundle.getBundle(index.toString())))
                index++
            }
        }

    private fun roundTripParcel(bundle: Bundle): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            val bytes = parcel.marshall()
            val restoredParcel = Parcel.obtain()
            try {
                restoredParcel.unmarshall(bytes, 0, bytes.size)
                restoredParcel.setDataPosition(0)
                requireNotNull(restoredParcel.readBundle(javaClass.classLoader))
            } finally {
                restoredParcel.recycle()
            }
        } finally {
            parcel.recycle()
        }
    }

    private fun collectBundleStrings(bundle: Bundle): List<String> =
        buildList {
            for (key in bundle.keySet()) {
                @Suppress("DEPRECATION")
                when (val value = bundle.get(key)) {
                    is String -> add(value)
                    is Bundle -> addAll(collectBundleStrings(value))
                }
            }
        }

    private fun replaceStringValue(
        source: Bundle,
        target: String,
        replacement: String,
    ): Bundle {
        val copy = Bundle()
        for (key in source.keySet()) {
            @Suppress("DEPRECATION")
            when (val value = source.get(key)) {
                is String -> copy.putString(key, if (value == target) replacement else value)
                is Bundle -> copy.putBundle(key, replaceStringValue(value, target, replacement))
                is Boolean -> copy.putBoolean(key, value)
                is Int -> copy.putInt(key, value)
                is Long -> copy.putLong(key, value)
                else -> copy.putAll(Bundle())
            }
        }
        return copy
    }
}
