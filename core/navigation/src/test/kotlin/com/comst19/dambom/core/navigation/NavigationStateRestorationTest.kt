package com.comst19.dambom.core.navigation

import android.os.Bundle
import android.os.Parcel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationStateRestorationTest {
    @get:Rule val composeRule = createComposeRule()

    private val config =
        NavigationConfig(
            startKey = TestHomeKey,
            bottomHomeKey = TestHomeKey,
            topLevelKeys = setOf(TestHomeKey, TestLibraryKey),
            bottomBarKeys = setOf(TestHomeKey, TestLibraryKey),
            topLevelBackBehavior = TopLevelBackBehavior.ExitThroughHome,
        )

    private val configWithStandalone =
        NavigationConfig(
            startKey = TestHomeKey,
            bottomHomeKey = TestHomeKey,
            topLevelKeys = setOf(TestHomeKey, TestLibraryKey, TestStandaloneKey),
            bottomBarKeys = setOf(TestHomeKey, TestLibraryKey),
            topLevelBackBehavior = TopLevelBackBehavior.ExitThroughHome,
        )

    private val serializersModule =
        SerializersModule {
            polymorphic(NavKey::class) {
                subclass(TestHomeKey::class)
                subclass(TestLibraryKey::class)
                subclass(TestStandaloneKey::class)
                subclass(TestWebKey::class)
                subclass(TestDetailKey::class)
            }
        }

    private val savedStateConfiguration =
        SavedStateConfiguration {
            serializersModule = this@NavigationStateRestorationTest.serializersModule
        }

    @Test
    fun `valid multi tab state with arguments survives compose restoration and parcel round trip`() {
        val restoration = StateRestorationTester(composeRule)
        lateinit var state: NavigationState

        restoration.setContent {
            state = rememberNavigationState(config = config, serializersModule = serializersModule)
        }

        composeRule.runOnIdle {
            val navigator = Navigator(state)
            navigator.navigate(TestWebKey(url = "https://example.com/watch", sharedLink = true))
            navigator.navigateTopLevel(TestLibraryKey)
            navigator.navigate(TestDetailKey(id = "video-99", isPrivate = true))
        }

        val saver = navigationStateSaver(config = config, serializersModule = serializersModule)
        val savedBundle =
            with(saver) {
                SaverScope { true }.save(state) as Bundle
            }
        assertFalse(
            "Encoded saved state must not embed JVM class names",
            bundleStrings(savedBundle).any { it.contains("com.comst19.dambom") },
        )
        val parcelRestored = saver.restore(roundTripParcel(savedBundle))
        assertNotNull(parcelRestored)
        assertEquals(TestLibraryKey, parcelRestored?.currentTopLevel)
        assertEquals(listOf(TestHomeKey, TestLibraryKey), parcelRestored?.topLevelsInUse)
        assertEquals(
            listOf(TestHomeKey, TestWebKey(url = "https://example.com/watch", sharedLink = true)),
            parcelRestored?.backStacks?.getValue(TestHomeKey)?.toList(),
        )
        assertEquals(
            listOf(TestLibraryKey, TestDetailKey(id = "video-99", isPrivate = true)),
            parcelRestored?.backStacks?.getValue(TestLibraryKey)?.toList(),
        )

        restoration.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            assertEquals(TestLibraryKey, state.currentTopLevel)
            assertEquals(listOf(TestHomeKey, TestLibraryKey), state.topLevelsInUse)
            assertEquals(
                listOf(TestHomeKey, TestWebKey(url = "https://example.com/watch", sharedLink = true)),
                state.backStacks.getValue(TestHomeKey).toList(),
            )
            assertEquals(
                listOf(TestLibraryKey, TestDetailKey(id = "video-99", isPrivate = true)),
                state.backStacks.getValue(TestLibraryKey).toList(),
            )

            val navigator = Navigator(state)
            navigator.goBack()
            assertEquals(TestLibraryKey, state.currentKey)
            navigator.goBack()
            assertEquals(TestHomeKey, state.currentTopLevel)
            assertEquals(TestWebKey(url = "https://example.com/watch", sharedLink = true), state.currentKey)
            navigator.goBack()
            assertEquals(TestHomeKey, state.currentKey)
        }
    }

    @Test
    fun `replace at tab root preserves root and other tab history across save and restore`() {
        val state = createInitialNavigationState(config)
        val navigator = Navigator(state)
        navigator.navigateTopLevel(TestLibraryKey)
        navigator.navigate(TestDetailKey(id = "lib-1", isPrivate = true))
        navigator.navigateTopLevel(TestHomeKey)

        navigator.replace(TestWebKey(url = "https://replace-from-root.example", sharedLink = false))
        assertEquals(
            listOf(TestHomeKey, TestWebKey(url = "https://replace-from-root.example", sharedLink = false)),
            state.backStacks.getValue(TestHomeKey).toList(),
        )

        val saver = navigationStateSaver(config = config, serializersModule = serializersModule)
        val savedBundle = with(saver) { SaverScope { true }.save(state) as Bundle }
        val restored = requireNotNull(saver.restore(roundTripParcel(savedBundle)))

        assertEquals(
            listOf(TestHomeKey, TestWebKey(url = "https://replace-from-root.example", sharedLink = false)),
            restored.backStacks.getValue(TestHomeKey).toList(),
        )
        assertEquals(
            listOf(TestLibraryKey, TestDetailKey(id = "lib-1", isPrivate = true)),
            restored.backStacks.getValue(TestLibraryKey).toList(),
        )
    }

    @Test
    fun `top level history validation accepts only navigator producible histories`() {
        val exitThroughHomeSaver = navigationStateSaver(configWithStandalone, serializersModule)
        val exitFromCurrentSaver =
            navigationStateSaver(
                configWithStandalone.copy(topLevelBackBehavior = TopLevelBackBehavior.ExitFromCurrent),
                serializersModule,
            )
        val validStacks =
            listOf(
                SavedTabBackStack(TestHomeKey, listOf(TestHomeKey)),
                SavedTabBackStack(TestLibraryKey, listOf(TestLibraryKey, TestDetailKey("v-1"))),
                SavedTabBackStack(TestStandaloneKey, listOf(TestStandaloneKey)),
            )

        // Valid under ExitThroughHome: normal tab selection and single-element setRoot(Library).
        val restoredTab =
            exitThroughHomeSaver.restore(
                encodeSnapshot(NavigationSavedSnapshot(listOf(TestHomeKey, TestLibraryKey), validStacks)),
            )
        assertEquals(listOf(TestHomeKey, TestLibraryKey), restoredTab?.topLevelsInUse)

        val setRootState = createInitialNavigationState(configWithStandalone)
        Navigator(setRootState).setRoot(TestLibraryKey)
        val setRootBundle = with(exitThroughHomeSaver) { SaverScope { true }.save(setRootState) as Bundle }
        val restoredSetRoot = exitThroughHomeSaver.restore(setRootBundle)
        assertEquals(listOf(TestLibraryKey), restoredSetRoot?.topLevelsInUse)
        assertEquals(TestLibraryKey, restoredSetRoot?.currentTopLevel)

        // Reversed [Library, Home] under ExitThroughHome must be rejected.
        assertNull(
            exitThroughHomeSaver.restore(
                encodeSnapshot(NavigationSavedSnapshot(listOf(TestLibraryKey, TestHomeKey), validStacks)),
            ),
        )
        // Standalone tab cannot have Home in its back history.
        assertNull(
            exitThroughHomeSaver.restore(
                encodeSnapshot(NavigationSavedSnapshot(listOf(TestHomeKey, TestStandaloneKey), validStacks)),
            ),
        )
        // History length > 2 is unreachable from Navigator.
        assertNull(
            exitThroughHomeSaver.restore(
                encodeSnapshot(
                    NavigationSavedSnapshot(listOf(TestHomeKey, TestLibraryKey, TestStandaloneKey), validStacks),
                ),
            ),
        )
        // Two-element history is unreachable under ExitFromCurrent.
        assertNull(
            exitFromCurrentSaver.restore(
                encodeSnapshot(NavigationSavedSnapshot(listOf(TestHomeKey, TestLibraryKey), validStacks)),
            ),
        )
    }

    @Test
    fun `legacy three backstack layout resets to home initial state and persists second navigation`() {
        assertLegacyUpgradeAndSecondSave(config = config)
    }

    @Test
    fun `legacy three backstack layout resets to non home start key and persists second navigation`() {
        assertLegacyUpgradeAndSecondSave(config = config.copy(startKey = TestLibraryKey))
    }

    private fun assertLegacyUpgradeAndSecondSave(config: NavigationConfig) {
        val harness = mountLegacyUpgradeHarness(config)
        composeRule.runOnIdle {
            harness.legacyTopHistory.clear()
            harness.legacyTopHistory.addAll(listOf(TestHomeKey, TestLibraryKey))
            harness.legacyLibraryStack.add(TestDetailKey("legacy-only-detail", isPrivate = true))
        }
        assertEquals("non-default-sibling", harness.observedSibling)

        val legacySaved = harness.activeRegistry.performSave()
        val legacyNavKeys = legacyNavBundleKeys(legacySaved)

        harness.mutateSibling = false
        harness.remount(savedValues = legacySaved, useLegacy = false)
        composeRule.runOnIdle {
            val state = requireNotNull(harness.newState)
            assertEquals("non-default-sibling", harness.observedSibling)
            assertInitialNavigationState(config, state)
            navigatePostUpgradeDetail(state)
        }

        val secondSaved = harness.activeRegistry.performSave()
        assertTrue(legacyNavKeys.all { it in secondSaved })
        val newNavSlotCount =
            secondSaved.entries.count { (key, values) -> key !in legacyNavKeys && values.singleOrNull() is Bundle }
        assertEquals(1, newNavSlotCount)

        harness.remount(savedValues = secondSaved, useLegacy = false)
        composeRule.runOnIdle {
            assertEquals("non-default-sibling", harness.observedSibling)
            assertPostUpgradeRestored(requireNotNull(harness.newState))
        }
    }

    private fun mountLegacyUpgradeHarness(config: NavigationConfig): LegacyUpgradeHarness {
        val harness = LegacyUpgradeHarness()
        composeRule.setContent {
            if (harness.mounted) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides harness.activeRegistry) {
                    if (harness.useLegacy) {
                        val legacy = legacyRememberNavigationState(config)
                        harness.legacyTopHistory = legacy.first
                        harness.legacyLibraryStack = legacy.second.getValue(TestLibraryKey)
                    } else {
                        harness.newState =
                            rememberNavigationState(config = config, serializersModule = serializersModule)
                    }
                    var sibling by rememberSaveable { mutableStateOf("default") }
                    if (harness.mutateSibling) sibling = "non-default-sibling"
                    harness.observedSibling = sibling
                }
            }
        }
        return harness
    }

    private inner class LegacyUpgradeHarness {
        var activeRegistry by mutableStateOf(SaveableStateRegistry(restoredValues = null, canBeSaved = { true }))
        var useLegacy by mutableStateOf(true)
        var mounted by mutableStateOf(true)
        var mutateSibling = true
        var observedSibling = ""
        lateinit var legacyTopHistory: NavBackStack<NavKey>
        lateinit var legacyLibraryStack: NavBackStack<NavKey>
        var newState: NavigationState? = null

        fun remount(
            savedValues: Map<String, List<Any?>>,
            useLegacy: Boolean,
        ) {
            val nextRegistry = SaveableStateRegistry(restoredValues = savedValues, canBeSaved = { true })
            composeRule.runOnIdle { mounted = false }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                this.useLegacy = useLegacy
                activeRegistry = nextRegistry
                mounted = true
            }
        }
    }

    private fun legacyNavBundleKeys(saved: Map<String, List<Any?>>): Set<String> {
        val keys =
            saved.entries
                .filter { (_, values) -> values.isNotEmpty() && values.all { it is Bundle } }
                .map { it.key }
                .toSet()
        assertEquals(3, keys.sumOf { saved.getValue(it).size })
        return keys
    }

    private fun assertInitialNavigationState(
        config: NavigationConfig,
        state: NavigationState,
    ) {
        assertEquals(config.initialTopLevelKeys, state.topLevelsInUse)
        assertEquals(config.startKey, state.currentTopLevel)
        assertEquals(listOf(TestHomeKey), state.backStacks.getValue(TestHomeKey).toList())
        assertEquals(listOf(TestLibraryKey), state.backStacks.getValue(TestLibraryKey).toList())
    }

    private fun navigatePostUpgradeDetail(state: NavigationState) {
        Navigator(state).apply {
            navigateTopLevel(TestHomeKey)
            navigate(TestWebKey(url = "https://after-upgrade.example/page", sharedLink = true))
            navigateTopLevel(TestLibraryKey)
            navigate(TestDetailKey(id = "after-upgrade-video", isPrivate = true))
        }
    }

    private fun assertPostUpgradeRestored(state: NavigationState) {
        assertEquals(TestLibraryKey, state.currentTopLevel)
        assertEquals(listOf(TestHomeKey, TestLibraryKey), state.topLevelsInUse)
        assertEquals(
            listOf(TestHomeKey, TestWebKey(url = "https://after-upgrade.example/page", sharedLink = true)),
            state.backStacks.getValue(TestHomeKey).toList(),
        )
        assertEquals(
            listOf(TestLibraryKey, TestDetailKey(id = "after-upgrade-video", isPrivate = true)),
            state.backStacks.getValue(TestLibraryKey).toList(),
        )
    }

    @Test
    fun `legacy nav key serializer payload resets only navigation state and preserves sibling saveables`() {
        val legacyBackStackBundle =
            encodeToSavedState(
                serializer = NavBackStackSerializer(NavKeySerializer()),
                value = NavBackStack<NavKey>(TestHomeKey, TestWebKey("https://legacy.example")),
            )

        assertFallbacksPreserveSiblingAndAllowNavigation(listOf(legacyBackStackBundle))
    }

    @Test
    fun `obfuscated legacy class name old fqn and unregistered key fall back without crash`() {
        val validBundle = validSavedBundle()
        val r8Corrupted = replaceStringValue(validBundle, "dambom.test.library", "q03")
        val fqnCorrupted =
            replaceStringValue(
                validBundle,
                "dambom.test.detail",
                "com.comst19.dambom.core.navigation.contract.LibraryGraph\$VideoDetailKey",
            )
        val unregisteredCorrupted = replaceStringValue(validBundle, "dambom.test.web", "dambom.unregistered_key")

        assertFallbacksPreserveSiblingAndAllowNavigation(
            listOf(r8Corrupted, fqnCorrupted, unregisteredCorrupted),
        )
    }

    @Test
    fun `empty stack wrong tab root missing tab and invalid history reset atomically`() {
        val saver = navigationStateSaver(config = config, serializersModule = serializersModule)
        val invalidSnapshots = invalidSnapshotBundles()

        invalidSnapshots.forEach { invalidPayload ->
            assertNull(saver.restore(invalidPayload))
        }
        assertNull(saver.restore("not-a-saved-state"))

        assertFallbacksPreserveSiblingAndAllowNavigation(
            listOf(invalidSnapshots[1], invalidSnapshots[2], "not-a-saved-state"),
        )
    }

    private fun invalidSnapshotBundles(): List<Bundle> {
        val homeWithWeb = SavedTabBackStack(TestHomeKey, listOf(TestHomeKey, TestWebKey("https://a.test")))
        val homeRoot = SavedTabBackStack(TestHomeKey, listOf(TestHomeKey))
        val libraryRoot = SavedTabBackStack(TestLibraryKey, listOf(TestLibraryKey))
        return listOf(
            encodeSnapshot(NavigationSavedSnapshot(emptyList(), listOf(homeWithWeb, libraryRoot))),
            encodeSnapshot(
                NavigationSavedSnapshot(
                    listOf(TestHomeKey, TestLibraryKey),
                    listOf(homeWithWeb, SavedTabBackStack(TestLibraryKey, emptyList())),
                ),
            ),
            encodeSnapshot(
                NavigationSavedSnapshot(
                    listOf(TestHomeKey, TestLibraryKey),
                    listOf(SavedTabBackStack(TestHomeKey, listOf(TestLibraryKey, TestDetailKey("1"))), libraryRoot),
                ),
            ),
            encodeSnapshot(NavigationSavedSnapshot(listOf(TestHomeKey), listOf(homeWithWeb))),
            encodeSnapshot(
                NavigationSavedSnapshot(
                    listOf(TestHomeKey, TestWebKey("https://a.test")),
                    listOf(homeRoot, libraryRoot),
                ),
            ),
            encodeSnapshot(
                NavigationSavedSnapshot(
                    listOf(TestHomeKey),
                    listOf(SavedTabBackStack(TestHomeKey, listOf(TestHomeKey, TestLibraryKey)), libraryRoot),
                ),
            ),
            encodeSnapshot(
                NavigationSavedSnapshot(listOf(TestHomeKey, TestHomeKey), listOf(homeRoot, libraryRoot)),
            ),
            encodeSnapshot(
                NavigationSavedSnapshot(listOf(TestLibraryKey, TestHomeKey), listOf(homeRoot, libraryRoot)),
            ),
        )
    }

    @Test
    fun `fallback uses configured non home start key initial history`() {
        val libraryStartConfig = config.copy(startKey = TestLibraryKey)
        val initialRegistry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
        var activeRegistry by mutableStateOf(initialRegistry)
        var mounted by mutableStateOf(true)
        var activeState: NavigationState? = null

        composeRule.setContent {
            if (mounted) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides activeRegistry) {
                    activeState = rememberNavigationState(libraryStartConfig, serializersModule)
                }
            }
        }
        composeRule.waitForIdle()

        val savedMap = initialRegistry.performSave()
        val navKey = savedMap.keys.single()
        val corruptRegistry =
            SaveableStateRegistry(
                restoredValues = mapOf(navKey to listOf(Bundle())),
                canBeSaved = { true },
            )
        composeRule.runOnIdle { mounted = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            activeRegistry = corruptRegistry
            mounted = true
        }
        composeRule.waitForIdle()

        val state = requireNotNull(activeState)
        assertEquals(listOf(TestHomeKey, TestLibraryKey), state.topLevelsInUse)
        assertEquals(TestLibraryKey, state.currentTopLevel)
        assertEquals(listOf(TestHomeKey), state.backStacks.getValue(TestHomeKey).toList())
        assertEquals(listOf(TestLibraryKey), state.backStacks.getValue(TestLibraryKey).toList())
    }

    private fun assertFallbacksPreserveSiblingAndAllowNavigation(corruptValues: List<Any>) {
        val seedRegistry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
        var activeRegistry by mutableStateOf(seedRegistry)
        var mounted by mutableStateOf(true)
        var mutateSibling = true
        lateinit var activeState: NavigationState
        var observedSibling = ""

        composeRule.setContent {
            if (mounted) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides activeRegistry) {
                    activeState = rememberNavigationState(config = config, serializersModule = serializersModule)
                    var sibling by rememberSaveable { mutableStateOf("initial") }
                    if (mutateSibling) sibling = "preserved-sibling"
                    observedSibling = sibling
                }
            }
        }
        composeRule.runOnIdle {
            Navigator(activeState).apply {
                navigate(TestWebKey("https://before-crash.example"))
                navigateTopLevel(TestLibraryKey)
                navigate(TestDetailKey("before-crash"))
            }
        }
        assertEquals("preserved-sibling", observedSibling)

        val savedValues = seedRegistry.performSave()
        val navEntryKey = savedValues.entries.single { (_, values) -> values.singleOrNull() is Bundle }.key
        val siblingEntryKey = savedValues.keys.single { it != navEntryKey }

        mutateSibling = false
        corruptValues.forEach { corruptValue ->
            val restoredRegistry =
                SaveableStateRegistry(
                    restoredValues =
                        mapOf(
                            navEntryKey to listOf(corruptValue),
                            siblingEntryKey to savedValues.getValue(siblingEntryKey),
                        ),
                    canBeSaved = { true },
                )
            composeRule.runOnIdle { mounted = false }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                activeRegistry = restoredRegistry
                mounted = true
            }
            composeRule.runOnIdle {
                assertEquals("preserved-sibling", observedSibling)
                assertRecoveredInitialNavigationAndInteractiveBack(activeState)
            }
        }
    }

    private fun assertRecoveredInitialNavigationAndInteractiveBack(state: NavigationState) {
        assertEquals(listOf(TestHomeKey), state.topLevelsInUse)
        assertEquals(TestHomeKey, state.currentTopLevel)
        assertEquals(TestHomeKey, state.currentKey)
        assertEquals(listOf(TestHomeKey), state.backStacks.getValue(TestHomeKey).toList())
        assertEquals(listOf(TestLibraryKey), state.backStacks.getValue(TestLibraryKey).toList())
        assertTrue(state.isAtRoot)

        val navigator = Navigator(state)
        navigator.navigateTopLevel(TestLibraryKey)
        navigator.navigate(TestDetailKey("after-recovery", isPrivate = true))
        assertEquals(TestLibraryKey, state.currentTopLevel)
        assertEquals(TestDetailKey("after-recovery", isPrivate = true), state.currentKey)

        navigator.goBack()
        assertEquals(TestLibraryKey, state.currentKey)
        navigator.goBack()
        assertEquals(TestHomeKey, state.currentTopLevel)
        assertEquals(TestHomeKey, state.currentKey)
        assertTrue(state.isAtRoot)
    }

    private fun validSavedBundle(): Bundle {
        val state = createInitialNavigationState(config)
        val navigator = Navigator(state)
        navigator.navigate(TestWebKey("https://example.com"))
        navigator.navigateTopLevel(TestLibraryKey)
        navigator.navigate(TestDetailKey("v1"))
        val saver = navigationStateSaver(config = config, serializersModule = serializersModule)
        return with(saver) { SaverScope { true }.save(state) as Bundle }
    }

    private fun encodeSnapshot(snapshot: NavigationSavedSnapshot): SavedState =
        encodeToSavedState(
            serializer = NavigationSavedSnapshot.serializer(),
            value = snapshot,
            configuration = savedStateConfiguration,
        )

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

    private fun bundleStrings(bundle: Bundle): List<String> =
        buildList {
            for (key in bundle.keySet()) {
                @Suppress("DEPRECATION")
                when (val value = bundle.get(key)) {
                    is String -> add(value)
                    is Bundle -> addAll(bundleStrings(value))
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

@Composable
private fun legacyRememberNavigationState(
    config: NavigationConfig,
): Pair<NavBackStack<NavKey>, Map<TopLevelNavKey, NavBackStack<NavKey>>> {
    val topLevelHistory = rememberNavBackStack(*config.initialTopLevelKeys.toTypedArray())
    val backStacks = config.topLevelKeys.associateWith { key -> rememberNavBackStack(key) }
    return topLevelHistory to backStacks
}

@Serializable
@SerialName("dambom.test.home")
internal data object TestHomeKey : TopLevelNavKey

@Serializable
@SerialName("dambom.test.library")
internal data object TestLibraryKey : TopLevelNavKey

@Serializable
@SerialName("dambom.test.standalone")
internal data object TestStandaloneKey : TopLevelNavKey

@Serializable
@SerialName("dambom.test.web")
internal data class TestWebKey(
    val url: String? = null,
    val sharedLink: Boolean = false,
) : AppNavKey

@Serializable
@SerialName("dambom.test.detail")
internal data class TestDetailKey(
    val id: String,
    val isPrivate: Boolean = false,
) : AppNavKey
