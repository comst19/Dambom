package com.comst19.dambom.core.network

import com.comst19.dambom.core.common.model.AppEnvironment
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkConfigTest {
    @Test
    fun `uses basic logging for debug and qa`() {
        assertEquals(
            HttpLoggingInterceptor.Level.BASIC,
            NetworkConfig.from("https://dev.example.invalid/", AppEnvironment.DEBUG).logLevel,
        )
        assertEquals(
            HttpLoggingInterceptor.Level.BASIC,
            NetworkConfig.from("https://qa.example.invalid/", AppEnvironment.QA).logLevel,
        )
    }

    @Test
    fun `disables logging for release`() {
        val config = NetworkConfig.from("https://api.example.invalid/", AppEnvironment.RELEASE)

        assertEquals("https://api.example.invalid/", config.baseUrl)
        assertEquals(HttpLoggingInterceptor.Level.NONE, config.logLevel)
    }
}
