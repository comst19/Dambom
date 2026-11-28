package com.comst19.dambom.core.network

import com.comst19.dambom.core.network.di.NetworkModule
import com.comst19.dambom.core.network.header.OkHttpMediaHeaderPolicy
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {
    @Test
    fun `preview client preserves default timeouts without logging or app interceptors`() {
        val defaults = OkHttpClient()
        val preview = NetworkModule.provideMediaPreviewOkHttpClient()

        assertTrue(preview.interceptors.isEmpty())
        assertTrue(preview.networkInterceptors.isEmpty())
        assertEquals(defaults.connectTimeoutMillis, preview.connectTimeoutMillis)
        assertEquals(defaults.readTimeoutMillis, preview.readTimeoutMillis)
        assertEquals(defaults.writeTimeoutMillis, preview.writeTimeoutMillis)
        assertEquals(defaults.callTimeoutMillis, preview.callTimeoutMillis)
    }

    @Test
    fun `header policy binding exposes OkHttp backed policy`() {
        val policy = OkHttpMediaHeaderPolicy()
        assertSame(policy, NetworkModule.provideMediaHeaderPolicy(policy))
    }
}
