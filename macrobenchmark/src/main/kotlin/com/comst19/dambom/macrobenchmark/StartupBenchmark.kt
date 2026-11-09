package com.comst19.dambom.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithoutCompilation() {
        measureColdStartup(CompilationMode.None())
    }

    @Test
    fun coldStartupWithBaselineProfile() {
        measureColdStartup(
            CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require,
            ),
        )
    }

    @Test
    fun openLibraryWithoutCompilation() {
        measureOpenLibrary(CompilationMode.None())
    }

    @Test
    fun openLibraryWithBaselineProfile() {
        measureOpenLibrary(
            CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require,
            ),
        )
    }

    @Test
    fun scrollPopulatedLibrary() {
        val firstTitle = InstrumentationRegistry.getArguments().getString("libraryFixtureFirstTitle").orEmpty()
        assumeTrue("Seed a populated library and pass libraryFixtureFirstTitle", firstTitle.isNotBlank())
        val firstItem = By.descContains(firstTitle)
        val showList = By.desc(Pattern.compile("목록으로 보기|View as list"))
        val scrollable = By.scrollable(true)
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
            iterations = 10,
            setupBlock = {
                killProcess()
                startActivityAndWait()
                device.wait(Until.findObject(By.text(CLIPBOARD_CONSENT_DENY_PATTERN)), UI_TIMEOUT_MILLIS)?.click()
                checkNotNull(device.wait(Until.findObject(By.text(LIBRARY_TAB_PATTERN)), UI_TIMEOUT_MILLIS)).click()
                device.findObject(showList)?.click()
                check(device.wait(Until.hasObject(firstItem), UI_TIMEOUT_MILLIS)) {
                    "The first fixture video is not visible: $firstTitle"
                }
            },
        ) {
            val collection = checkNotNull(device.findObject(scrollable))
            collection.setGestureMargin(device.displayWidth / 5)
            repeat(3) { collection.swipe(Direction.UP, 0.65f) }
            check(device.wait(Until.gone(firstItem), UI_TIMEOUT_MILLIS)) {
                "The library did not scroll past the first fixture video"
            }
        }
    }

    private fun measureColdStartup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }
    }

    private fun measureOpenLibrary(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 10,
            setupBlock = {
                killProcess()
                pressHome()
                startActivityAndWait()

                device.wait(Until.findObject(By.text(CLIPBOARD_CONSENT_DENY_PATTERN)), UI_TIMEOUT_MILLIS)?.click()
                check(device.wait(Until.hasObject(By.text(LIBRARY_TAB_PATTERN)), UI_TIMEOUT_MILLIS)) {
                    "Library bottom navigation item was not found"
                }
            },
        ) {
            device.findObject(By.text(LIBRARY_TAB_PATTERN)).click()
            check(device.wait(Until.hasObject(By.text(LIBRARY_SEARCH_PATTERN)), UI_TIMEOUT_MILLIS)) {
                "Library screen did not become visible"
            }
        }
    }
}
