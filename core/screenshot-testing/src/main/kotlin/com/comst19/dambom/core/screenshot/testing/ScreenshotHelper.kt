package com.comst19.dambom.core.screenshot.testing

import androidx.compose.ui.test.SemanticsNodeInteraction
import com.github.takahirom.roborazzi.captureRoboImage

fun SemanticsNodeInteraction.captureScreenshot(filePath: String) {
    captureRoboImage(filePath)
}
