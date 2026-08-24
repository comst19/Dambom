package com.comst19.dambom.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAccessStateTest {
    @Test
    fun `offline blocks internet and downloads`() {
        val state = NetworkAccessState(NetworkConnection.OFFLINE, wifiOnlyDownloads = false)

        assertFalse(state.canUseInternet)
        assertFalse(state.canDownload)
        assertEquals(NetworkRestriction.OFFLINE, state.restriction)
    }

    @Test
    fun `metered network blocks only wifi restricted downloads`() {
        val unrestricted = NetworkAccessState(NetworkConnection.METERED, wifiOnlyDownloads = false)
        val wifiOnly = NetworkAccessState(NetworkConnection.METERED, wifiOnlyDownloads = true)

        assertTrue(unrestricted.canDownload)
        assertFalse(wifiOnly.canDownload)
        assertEquals(NetworkRestriction.UNMETERED_REQUIRED, wifiOnly.restriction)
    }

    @Test
    fun `unmetered network allows wifi restricted downloads`() {
        val state = NetworkAccessState(NetworkConnection.UNMETERED, wifiOnlyDownloads = true)

        assertTrue(state.canUseInternet)
        assertTrue(state.canDownload)
        assertNull(state.restriction)
    }
}
