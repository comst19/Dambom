package com.comst19.dambom.feature.web

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35])
class WebClipboardTest {
    @Test
    fun `copy writes the link and only older Android shows an app toast`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowToast.reset()

        context.copyLink("https://example.com/video", "Copied")

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertEquals(
            "https://example.com/video",
            clipboard.primaryClip
                ?.getItemAt(0)
                ?.text
                .toString(),
        )
        assertEquals(if (Build.VERSION.SDK_INT < 33) "Copied" else null, ShadowToast.getTextOfLatestToast())
    }
}
