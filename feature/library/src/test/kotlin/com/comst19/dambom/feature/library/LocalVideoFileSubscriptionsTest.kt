package com.comst19.dambom.feature.library

import android.os.FileObserver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.comst19.dambom.feature.library.file.LocalVideoFileObservers
import com.comst19.dambom.feature.library.file.observeLocalVideoChanges
import com.comst19.dambom.feature.library.file.rememberLocalVideoAvailable
import com.comst19.dambom.feature.library.media.observeLocalVideoCacheKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    instrumentedPackages = ["com.comst19.dambom.feature.library.media", "com.comst19.dambom.feature.library.file"],
)
class LocalVideoFileSubscriptionsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `availability receives deletion and restoration after metadata subscriber leaves`() {
        verifyRemainingSubscriber(removeMetadata = true)
    }

    @Test
    fun `metadata receives deletion and restoration after availability subscriber leaves`() {
        verifyRemainingSubscriber(removeMetadata = false)
    }

    private fun verifyRemainingSubscriber(removeMetadata: Boolean) {
        val directory = Files.createTempDirectory("shared-video").toFile()
        val file = directory.resolve("video.mp4").apply { writeBytes(byteArrayOf(1)) }
        val showAvailability = mutableStateOf(true)
        val showMetadata = mutableStateOf(true)
        var available = false
        val size = AtomicLong(-1L)
        try {
            composeRule.setContent {
                if (showAvailability.value) {
                    available = rememberLocalVideoAvailable(libraryTestVideo(file.path)).value
                }
                if (showMetadata.value) {
                    LaunchedEffect(file.path) {
                        observeLocalVideoCacheKey(file.path).collect { size.set(it.sizeBytes) }
                    }
                }
            }
            composeRule.waitUntil(5_000) { available && size.get() == 1L }
            assertTrue(file.delete())
            composeRule.waitUntil(5_000) { !available && size.get() == 0L }
            file.writeBytes(byteArrayOf(1, 2))
            composeRule.waitUntil(5_000) { available && size.get() == 2L }
            composeRule.runOnIdle {
                showMetadata.value = !removeMetadata
                showAvailability.value = removeMetadata
            }
            composeRule.waitForIdle()
            assertTrue(file.delete())
            composeRule.waitUntil(5_000) { if (removeMetadata) !available else size.get() == 0L }
            file.writeBytes(byteArrayOf(1, 2, 3))
            composeRule.waitUntil(5_000) { if (removeMetadata) available else size.get() == 3L }
            composeRule.runOnIdle {
                showMetadata.value = false
                showAvailability.value = false
            }
            composeRule.waitForIdle()
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `metadata resubscription emits current revision while another subscription stays active`() =
        runBlocking {
            val directory = Files.createTempDirectory("resubscribed-video").toFile()
            val file = directory.resolve("video.mp4").apply { writeBytes(byteArrayOf(1)) }
            val events = Channel<Unit>(Channel.UNLIMITED)
            val survivor = launch(Dispatchers.IO) { observeLocalVideoChanges(file.path).collect { events.send(it) } }
            try {
                withTimeout(5_000) { events.receive() }
                assertEquals(1L, withTimeout(5_000) { observeLocalVideoCacheKey(file.path).first().sizeBytes })
                file.writeBytes(byteArrayOf(1, 2, 3))
                assertEquals(3L, withTimeout(5_000) { observeLocalVideoCacheKey(file.path).first().sizeBytes })
                assertTrue(file.delete())
                assertEquals(0L, withTimeout(5_000) { observeLocalVideoCacheKey(file.path).first().sizeBytes })
            } finally {
                survivor.cancelAndJoin()
                directory.deleteRecursively()
            }
        }

    @Test
    fun `directory watch fans out only matching file and stops only after last subscription`() {
        var starts = 0
        var stops = 0
        var creations = 0
        lateinit var event: (String?) -> Unit
        val observers =
            LocalVideoFileObservers { directory, onChange ->
                creations++
                event = onChange
                fakeObserver(directory, { starts++ }, { stops++ })
            }
        val file = File("/tmp/subscription-test/video.mp4")
        var first = 0
        var second = 0
        val unsubscribeFirst = observers.subscribe(file) { first++ }
        val unsubscribeSecond = observers.subscribe(file) { second++ }
        assertEquals(1, creations)
        assertEquals(1, starts)
        event("other.mp4")
        event(null)
        assertEquals(0, first)
        event(file.name)
        assertEquals(1, first)
        assertEquals(1, second)
        unsubscribeFirst()
        unsubscribeFirst()
        assertEquals(0, stops)
        event(file.name)
        assertEquals(1, first)
        assertEquals(2, second)
        unsubscribeSecond()
        assertEquals(1, stops)
        event(file.name)
        assertEquals(2, second)
        val unsubscribeNew = observers.subscribe(file) { first++ }
        assertEquals(2, starts)
        assertEquals(2, creations)
        unsubscribeFirst()
        assertEquals(1, stops)
        unsubscribeNew()
        assertEquals(2, stops)
    }

    @Test
    fun `initial notification reads change made while watch starts and collection disposes watch`() =
        runBlocking {
            val file = File.createTempFile("watch-gap", ".mp4")
            var stopped = false
            val observers =
                LocalVideoFileObservers { directory, _ ->
                    fakeObserver(directory, { assertTrue(file.delete()) }, { stopped = true })
                }
            assertTrue(file.isFile)
            observeLocalVideoChanges(file.path, observers).first()
            assertFalse(file.isFile)
            assertTrue(stopped)
        }

    @Suppress("DEPRECATION")
    private fun fakeObserver(
        directory: File,
        onStart: () -> Unit,
        onStop: () -> Unit,
    ): FileObserver =
        object : FileObserver(directory.path) {
            override fun onEvent(
                event: Int,
                path: String?,
            ) = Unit

            override fun startWatching() = onStart()

            override fun stopWatching() = onStop()
        }
}
