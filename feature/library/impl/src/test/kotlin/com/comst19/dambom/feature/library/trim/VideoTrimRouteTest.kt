package com.comst19.dambom.feature.library.trim

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoTrimRouteTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `repeated save taps launch only one document picker until result returns`() {
        val registry = RecordingDocumentRegistry()
        val selected = mutableListOf<Uri>()
        lateinit var launchExport: (String) -> Unit
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner) {
                launchExport =
                    rememberTrimExportLauncher(
                        exporting = false,
                        onDestinationSelected = { selected += it },
                    )
            }
        }
        compose.runOnIdle {
            launchExport("video-clip.mp4")
            launchExport("video-clip.mp4")
        }
        assertEquals(1, registry.launchCount)
        assertEquals(listOf("video-clip.mp4"), registry.inputs)
        compose.runOnIdle {
            registry.dispatchResult(registry.lastRequestCode, Activity.RESULT_CANCELED, null)
        }
        assertEquals(emptyList<Uri>(), selected)
        compose.runOnIdle {
            launchExport("video-clip.mp4")
        }
        assertEquals(2, registry.launchCount)
    }

    @Test
    fun `picker in progress survives state restoration and forwards returned uri once`() {
        val registry = RecordingDocumentRegistry()
        val selected = mutableListOf<Uri>()
        lateinit var launchExport: (String) -> Unit
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner) {
                launchExport =
                    rememberTrimExportLauncher(
                        exporting = false,
                        onDestinationSelected = { selected += it },
                    )
            }
        }
        compose.runOnIdle { launchExport("clip.mp4") }
        assertEquals(1, registry.launchCount)
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { launchExport("clip.mp4") }
        assertEquals(1, registry.launchCount)
        val destination = Uri.parse("content://test.documents/restored-clip")
        compose.runOnIdle {
            registry.dispatchResult(
                registry.lastRequestCode,
                Activity.RESULT_OK,
                Intent().setData(destination),
            )
        }
        assertEquals(listOf(destination), selected)
        compose.runOnIdle { launchExport("clip-2.mp4") }
        assertEquals(2, registry.launchCount)
    }

    @Test
    fun `picker ignores taps while exporting and recovers after ActivityNotFoundException`() {
        val registry = RecordingDocumentRegistry()
        var exporting by mutableStateOf(true)
        lateinit var launchExport: (String) -> Unit
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner) {
                launchExport =
                    rememberTrimExportLauncher(
                        exporting = exporting,
                        onDestinationSelected = {},
                    )
            }
        }
        compose.runOnIdle { launchExport("clip.mp4") }
        assertEquals(0, registry.launchCount)
        exporting = false
        registry.failNextLaunch = true
        compose.runOnIdle { launchExport("clip.mp4") }
        assertEquals(1, registry.launchCount)
        compose.runOnIdle { launchExport("clip.mp4") }
        assertEquals(2, registry.launchCount)
    }

    private class RecordingDocumentRegistry : ActivityResultRegistry() {
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry = this@RecordingDocumentRegistry
            }
        var lastRequestCode = 0
        var launchCount = 0
        var failNextLaunch = false
        val inputs = mutableListOf<String>()

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            lastRequestCode = requestCode
            launchCount++
            if (input is String) inputs += input
            if (failNextLaunch) {
                failNextLaunch = false
                throw ActivityNotFoundException("No document picker")
            }
        }
    }
}
