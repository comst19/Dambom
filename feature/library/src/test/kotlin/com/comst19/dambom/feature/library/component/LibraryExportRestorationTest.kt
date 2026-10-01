package com.comst19.dambom.feature.library.component

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.LibraryTestDownloads
import com.comst19.dambom.feature.library.LibraryTestSettings
import com.comst19.dambom.feature.library.LibraryViewModel
import com.comst19.dambom.feature.library.file.LibraryFileManager
import com.comst19.dambom.feature.library.libraryTestVideo
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryExportRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `document result after saved state recreation resolves task id and copies original bytes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = context.filesDir.resolve("restored-export.mp4").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val task = libraryTestVideo(source.path)
        val repository = LibraryTestDownloads(listOf(task))
        val destination = Uri.parse("content://export/document/restored")
        val output = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(destination, output)
        val registry = ExportRegistry()
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }
        lateinit var actions: LibraryFileActions
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                actions =
                    rememberLibraryFileActions(
                        androidx.compose.runtime.remember {
                            LibraryViewModel(
                                repository,
                                LibraryTestSettings,
                                SpyNavigationDispatcher(),
                                SavedStateHandle(),
                                LibraryFileManager(context, Dispatchers.Unconfined),
                                AppEventBus(),
                            )
                        },
                    )
            }
        }
        compose.runOnIdle { actions.onExport(task) }
        assertEquals(1, registry.launchCount)
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            repository.downloads.value = listOf(task.copy(title = "New title"))
            registry.dispatchResult(registry.requestCode, Activity.RESULT_OK, Intent().setData(destination))
        }
        compose.waitUntil { output.size() == 3 }
        assertArrayEquals(source.readBytes(), output.toByteArray())
        source.delete()
    }

    private class ExportRegistry : ActivityResultRegistry() {
        var requestCode = 0
        var launchCount = 0

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            this.requestCode = requestCode
            launchCount++
        }
    }
}
