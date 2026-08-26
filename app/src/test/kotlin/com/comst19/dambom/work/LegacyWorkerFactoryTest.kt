package com.comst19.dambom.work

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyWorkerFactoryTest {
    @Test
    fun `legacy download worker name maps to current package`() {
        assertEquals(
            "com.comst19.dambom.core.data.download.DownloadQueueWorker",
            "com.comst19.dambom.core.data.repository.DownloadQueueWorker".remapLegacyWorkerClassName(),
        )
    }

    @Test
    fun `current and unrelated worker names stay unchanged`() {
        val currentName = "com.comst19.dambom.core.data.download.DownloadQueueWorker"
        val unrelatedName = "example.UnrelatedWorker"

        assertEquals(currentName, currentName.remapLegacyWorkerClassName())
        assertEquals(unrelatedName, unrelatedName.remapLegacyWorkerClassName())
    }
}
