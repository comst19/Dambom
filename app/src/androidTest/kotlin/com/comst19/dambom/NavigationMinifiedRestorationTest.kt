package com.comst19.dambom

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.feature.detection.api.DetectionResultKey
import com.comst19.dambom.feature.home.api.HomeKey
import com.comst19.dambom.feature.library.api.LibraryKey
import com.comst19.dambom.feature.library.api.VideoDetailKey
import com.comst19.dambom.feature.web.api.WebKey
import com.comst19.dambom.presentation.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Arrays
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class NavigationMinifiedRestorationTest {
    @Test
    fun minifiedMainActivityPreservesTabsHistoryAndKeyArgumentsAcrossRecreation() {
        if (BuildConfig.APP_ENVIRONMENT != "DEBUG") {
            assertNotEquals("com.comst19.dambom.feature.home.api.HomeKey", HomeKey::class.java.name)
            assertNotEquals("com.comst19.dambom.feature.web.api.WebKey", WebKey::class.java.name)
            assertNotEquals(
                "com.comst19.dambom.feature.detection.api.DetectionResultKey",
                DetectionResultKey::class.java.name,
            )
            assertNotEquals(
                "com.comst19.dambom.feature.library.api.VideoDetailKey",
                VideoDetailKey::class.java.name,
            )
        }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                runBlocking {
                    activity.navigationDispatcher.dispatch(
                        NavigationEvent.Navigate(
                            WebKey(url = WEB_URL, sharedLink = false),
                        ),
                    )
                    activity.navigationDispatcher.dispatch(
                        NavigationEvent.Navigate(
                            DetectionResultKey(
                                url = DETECTION_URL,
                                snapshotId = SNAPSHOT_ID,
                                requestId = REQUEST_ID,
                                sharedLink = true,
                            ),
                        ),
                    )
                    activity.navigationDispatcher.dispatch(NavigationEvent.NavigateTopLevel(LibraryKey))
                    activity.navigationDispatcher.dispatch(
                        NavigationEvent.Navigate(VideoDetailKey(id = VIDEO_ID, isPrivate = true)),
                    )
                }
            }

            val savedBeforeRecreate =
                awaitSavedSnapshot(scenario) { snapshot ->
                    snapshot.hasLibraryEntryType("dambom.video_detail")
                }
            assertFullExpectedSnapshot(savedBeforeRecreate)

            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)

            val savedAfterRecreate =
                awaitSavedSnapshot(scenario) { snapshot ->
                    snapshot.hasLibraryEntryType("dambom.video_detail")
                }
            assertFullExpectedSnapshot(savedAfterRecreate)

            // Back #1: pops VideoDetailKey from Library tab stack.
            dispatchBack(scenario)
            val afterPopVideoDetail =
                awaitSavedSnapshot(scenario) { snapshot ->
                    !snapshot.hasLibraryEntryType("dambom.video_detail")
                }
            assertEquals(Arrays.asList("dambom.home", "dambom.library"), afterPopVideoDetail.topLevelHistory)
            assertEquals(1, afterPopVideoDetail.libraryStack().size)
            assertEquals("dambom.library", afterPopVideoDetail.libraryStack()[0].type)
            assertHomeStackWithWebAndDetection(afterPopVideoDetail.homeStack())

            // Back #2: pops LibraryKey from topLevelHistory back to Home tab.
            dispatchBack(scenario)
            // Back #3: pops DetectionResultKey from Home tab stack.
            dispatchBack(scenario)
            val afterPopDetection =
                awaitSavedSnapshot(scenario) { snapshot ->
                    snapshot.homeStack().size == 2 && snapshot.topLevelHistory.size == 1
                }
            assertEquals(Arrays.asList("dambom.home"), afterPopDetection.topLevelHistory)
            assertEquals(2, afterPopDetection.homeStack().size)
            assertEquals("dambom.home", afterPopDetection.homeStack()[0].type)
            assertWebEntry(afterPopDetection.homeStack()[1])

            // Back #4: pops WebKey from Home tab stack, returning to HomeKey root.
            dispatchBack(scenario)
            val afterPopWeb =
                awaitSavedSnapshot(scenario) { snapshot ->
                    snapshot.homeStack().size == 1
                }
            assertEquals(Arrays.asList("dambom.home"), afterPopWeb.topLevelHistory)
            assertEquals(1, afterPopWeb.homeStack().size)
            assertEquals("dambom.home", afterPopWeb.homeStack()[0].type)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        } finally {
            scenario.close()
        }
    }

    private fun dispatchBack(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            runBlocking {
                activity.navigationDispatcher.dispatch(NavigationEvent.Back)
            }
        }
    }

    private fun assertFullExpectedSnapshot(snapshot: ParsedNavigationSnapshot) {
        assertEquals(Arrays.asList("dambom.home", "dambom.library"), snapshot.topLevelHistory)
        assertHomeStackWithWebAndDetection(snapshot.homeStack())

        val libraryStack = snapshot.libraryStack()
        assertEquals(2, libraryStack.size)
        assertEquals("dambom.library", libraryStack[0].type)
        val videoDetail = libraryStack[1]
        assertEquals("dambom.video_detail", videoDetail.type)
        assertEquals(VIDEO_ID, videoDetail.id)
        assertEquals(true, videoDetail.isPrivate)

        val packagePattern = Pattern.compile("com\\.comst19\\.dambom\\.feature")
        for (raw in snapshot.rawStrings) {
            assertFalse(packagePattern.matcher(raw).find())
        }
    }

    private fun assertHomeStackWithWebAndDetection(homeStack: List<ParsedNavEntry>) {
        assertEquals(3, homeStack.size)
        assertEquals("dambom.home", homeStack[0].type)
        assertWebEntry(homeStack[1])
        val detection = homeStack[2]
        assertEquals("dambom.detection_result", detection.type)
        assertEquals(DETECTION_URL, detection.url)
        assertEquals(SNAPSHOT_ID, detection.snapshotId)
        assertEquals(REQUEST_ID, detection.requestId)
        assertEquals(true, detection.sharedLink)
    }

    private fun assertWebEntry(web: ParsedNavEntry) {
        assertEquals("dambom.web", web.type)
        assertEquals(WEB_URL, web.url)
        assertEquals(false, web.sharedLink)
    }

    private fun awaitSavedSnapshot(
        scenario: ActivityScenario<MainActivity>,
        predicate: (ParsedNavigationSnapshot) -> Boolean,
    ): ParsedNavigationSnapshot {
        val deadline = System.currentTimeMillis() + 10_000L
        var latest: ParsedNavigationSnapshot? = null
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val outState = Bundle()
                InstrumentationRegistry
                    .getInstrumentation()
                    .callActivityOnSaveInstanceState(activity, outState)
                latest = parseNavigationSnapshot(outState)
            }
            val current = latest
            if (current != null && predicate(current)) return current
            Thread.sleep(100L)
        }
        return requireNotNull(latest) { "Navigation snapshot bundle was not found in Activity saved state" }
    }

    private fun parseNavigationSnapshot(root: Bundle): ParsedNavigationSnapshot? {
        val snapshotBundle = findSnapshotBundle(root) ?: return null
        val topLevelHistoryBundle = snapshotBundle.getBundle("topLevelHistory") ?: return null
        val backStacksBundle = snapshotBundle.getBundle("backStacks") ?: return null

        val topLevelHistory = ArrayList<String>()
        for (entryBundle in indexedBundles(topLevelHistoryBundle)) {
            val type = entryBundle.getString("type")
            if (type != null) topLevelHistory.add(type)
        }

        val stacksByRoot = HashMap<String, List<ParsedNavEntry>>()
        for (tabBundle in indexedBundles(backStacksBundle)) {
            val rootKeyType = tabBundle.getBundle("rootKey")?.getString("type") ?: continue
            val stackBundle = tabBundle.getBundle("stack") ?: continue
            val entries = ArrayList<ParsedNavEntry>()
            for (itemBundle in indexedBundles(stackBundle)) {
                entries.add(parseNavEntry(itemBundle))
            }
            stacksByRoot[rootKeyType] = entries
        }

        return ParsedNavigationSnapshot(
            topLevelHistory = topLevelHistory,
            stacksByRoot = stacksByRoot,
            rawStrings = collectStrings(snapshotBundle),
        )
    }

    private fun parseNavEntry(bundle: Bundle): ParsedNavEntry {
        val type = requireNotNull(bundle.getString("type"))
        val value = bundle.getBundle("value")
        assertNotNull("Missing value bundle for $type", value)
        return ParsedNavEntry(
            type = type,
            url = value?.getString("url"),
            sharedLink = value?.getBoolean("sharedLink", false) ?: false,
            snapshotId = value?.getString("snapshotId"),
            requestId = value?.getString("requestId"),
            id = value?.getString("id"),
            isPrivate = value?.getBoolean("isPrivate", false) ?: false,
        )
    }

    private fun findSnapshotBundle(bundle: Bundle): Bundle? {
        if (bundle.containsKey("topLevelHistory") && bundle.containsKey("backStacks")) {
            return bundle
        }
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            when (val value = bundle.get(key)) {
                is Bundle -> {
                    val found = findSnapshotBundle(value)
                    if (found != null) return found
                }

                is ArrayList<*> -> {
                    for (item in value) {
                        if (item is Bundle) {
                            val found = findSnapshotBundle(item)
                            if (found != null) return found
                        }
                    }
                }
            }
        }
        return null
    }

    private fun indexedBundles(bundle: Bundle): List<Bundle> {
        val result = ArrayList<Bundle>()
        var index = 0
        while (bundle.containsKey(index.toString())) {
            val child = bundle.getBundle(index.toString())
            if (child != null) result.add(child)
            index++
        }
        return result
    }

    private fun collectStrings(bundle: Bundle): List<String> {
        val strings = ArrayList<String>()

        fun visit(current: Bundle) {
            for (key in current.keySet()) {
                @Suppress("DEPRECATION")
                when (val value = current.get(key)) {
                    is String -> strings.add(value)
                    is Bundle -> visit(value)
                }
            }
        }
        visit(bundle)
        return strings
    }

    private data class ParsedNavigationSnapshot(
        val topLevelHistory: List<String>,
        val stacksByRoot: Map<String, List<ParsedNavEntry>>,
        val rawStrings: List<String>,
    ) {
        fun homeStack(): List<ParsedNavEntry> = stacksByRoot["dambom.home"] ?: ArrayList()

        fun libraryStack(): List<ParsedNavEntry> = stacksByRoot["dambom.library"] ?: ArrayList()

        fun hasLibraryEntryType(type: String): Boolean {
            for (entry in libraryStack()) {
                if (entry.type == type) return true
            }
            return false
        }
    }

    private data class ParsedNavEntry(
        val type: String,
        val url: String?,
        val sharedLink: Boolean,
        val snapshotId: String?,
        val requestId: String?,
        val id: String?,
        val isPrivate: Boolean,
    )

    companion object {
        private const val WEB_URL = "https://web.example.com/browse?tab=1"
        private const val DETECTION_URL = "https://detect.example.com/watch?v=42"
        private const val SNAPSHOT_ID = "snap-42"
        private const val REQUEST_ID = "req-99"
        private const val VIDEO_ID = "video-77"
    }
}
