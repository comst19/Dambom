package com.comst19.dambom.core.data.repository

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadWorkSchedulerTest {
    @Test
    fun `wifi setting selects the matching WorkManager network constraint`() {
        assertEquals(NetworkType.CONNECTED, requiredNetworkType(wifiOnlyDownloads = false))
        assertEquals(NetworkType.UNMETERED, requiredNetworkType(wifiOnlyDownloads = true))
    }
}
