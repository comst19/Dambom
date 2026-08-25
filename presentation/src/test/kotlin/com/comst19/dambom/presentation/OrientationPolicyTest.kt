package com.comst19.dambom.presentation

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationPolicyTest {
    @Test
    fun `compact widths stay portrait and larger widths allow rotation`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            requestedOrientationFor(smallestScreenWidthDp = 599),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            requestedOrientationFor(smallestScreenWidthDp = 600),
        )
    }
}
