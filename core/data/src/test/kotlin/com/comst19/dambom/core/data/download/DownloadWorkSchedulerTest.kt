package com.comst19.dambom.core.data.download

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWorkSchedulerTest {
    @Test
    fun `wifi setting selects the matching WorkManager network constraint`() {
        assertEquals(NetworkType.CONNECTED, requiredNetworkType(wifiOnlyDownloads = false))
        assertEquals(NetworkType.UNMETERED, requiredNetworkType(wifiOnlyDownloads = true))
    }

    @Test
    fun `checkpoint requires enough bytes and elapsed time`() {
        assertFalse(shouldCheckpoint(bytesSinceLastCheckpoint = 1024L * 1024L, millisSinceLastCheckpoint = 499L))
        assertFalse(shouldCheckpoint(bytesSinceLastCheckpoint = 512L * 1024L, millisSinceLastCheckpoint = 1_000L))
        assertTrue(shouldCheckpoint(bytesSinceLastCheckpoint = 1024L * 1024L, millisSinceLastCheckpoint = 500L))
    }
}
