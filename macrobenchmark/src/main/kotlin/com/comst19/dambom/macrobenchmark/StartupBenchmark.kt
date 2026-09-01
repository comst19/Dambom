package com.comst19.dambom.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
