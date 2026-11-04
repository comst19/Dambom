package com.comst19.dambom.feature.library.file

import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class MainSafeFileOperationsTest {
    @Test
    fun fileChecksObserverSubscriptionAndSharePreparationDoNotReadDiskOnMain() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().context
            val file = context.cacheDir.resolve("main-safe-file-test.mp4").apply { writeBytes(byteArrayOf(1)) }
            val task =
                DownloadTask(
                    id = "main-safe-file-test",
                    url = "https://example.test/video.mp4",
                    sourcePageUrl = "https://example.test",
                    title = "Video",
                    mimeType = "video/mp4",
                    expectedBytes = 1L,
                    downloadedBytes = 1L,
                    quality = "original",
                    status = DownloadStatus.COMPLETED,
                    failureReason = null,
                    localFileName = file.name,
                    localFilePath = file.path,
                    createdAtMillis = 0L,
                    updatedAtMillis = 0L,
                )
            val manager = LibraryFileManager(context, Dispatchers.IO)
            val violations = CopyOnWriteArrayList<Throwable>()
            try {
                isLocalVideoAvailable(task)
                manager.createShareIntent(task)
                observeLocalVideoChanges(file.path).first()

                withContext(Dispatchers.Main) {
                    val previous = StrictMode.getThreadPolicy()
                    StrictMode.setThreadPolicy(
                        StrictMode.ThreadPolicy
                            .Builder()
                            .detectDiskReads()
                            .detectDiskWrites()
                            .penaltyListener({ it.run() }) { violations += it }
                            .build(),
                    )
                    try {
                        assertTrue(isLocalVideoAvailable(task))
                        assertFalse(isLocalVideoAvailable(task.copy(localFilePath = "${file.path}.missing")))
                        observeLocalVideoChanges(file.path).first()
                        assertNull(manager.createShareIntent(task))
                    } finally {
                        StrictMode.setThreadPolicy(previous)
                    }
                }

                assertTrue(violations.joinToString("\n") { it.stackTraceToString() }, violations.isEmpty())
            } finally {
                file.delete()
            }
        }
}
