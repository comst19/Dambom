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
    fun `checkpoint rate is bounded by time without starving slow downloads`() {
        assertFalse(shouldCheckpoint(bytesSinceLastCheckpoint = 1024L * 1024L, millisSinceLastCheckpoint = 499L))
        assertTrue(shouldCheckpoint(bytesSinceLastCheckpoint = 512L * 1024L, millisSinceLastCheckpoint = 1_000L))
        assertTrue(shouldCheckpoint(bytesSinceLastCheckpoint = 32L * 1024L, millisSinceLastCheckpoint = 500L))
        assertFalse(shouldCheckpoint(bytesSinceLastCheckpoint = 0L, millisSinceLastCheckpoint = 1_000L))
        assertTrue(shouldCheckpoint(bytesSinceLastCheckpoint = 1024L * 1024L, millisSinceLastCheckpoint = 500L))
    }
}
