package com.comst19.dambom.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    @Test
    fun openLibrary() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startActivityAndWait()

            device.wait(Until.findObject(By.text(CLIPBOARD_CONSENT_DENY_PATTERN)), UI_TIMEOUT_MILLIS)?.click()
            val libraryTab = device.wait(Until.findObject(By.text(LIBRARY_TAB_PATTERN)), UI_TIMEOUT_MILLIS)
            checkNotNull(libraryTab) { "Library bottom navigation item was not found" }
            libraryTab.click()
            check(device.wait(Until.hasObject(By.text(LIBRARY_SEARCH_PATTERN)), UI_TIMEOUT_MILLIS)) {
                "Library screen did not become visible"
            }
        }
    }
}

internal const val TARGET_PACKAGE = "com.comst19.dambom"
internal const val UI_TIMEOUT_MILLIS = 5_000L
internal val CLIPBOARD_CONSENT_DENY_PATTERN = Pattern.compile("직접 붙여넣기|Paste manually")
internal val LIBRARY_TAB_PATTERN = Pattern.compile("보관함|Library")
internal val LIBRARY_SEARCH_PATTERN = Pattern.compile("저장한 영상 검색|Search saved videos")
