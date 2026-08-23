package com.comst19.dambom.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppEnvironmentTest {
    @Test
    fun `parses supported build environment names`() {
        assertEquals(AppEnvironment.DEBUG, AppEnvironment.from("DEBUG"))
        assertEquals(AppEnvironment.QA, AppEnvironment.from("QA"))
        assertEquals(AppEnvironment.RELEASE, AppEnvironment.from("RELEASE"))
    }

    @Test
    fun `rejects unsupported build environment names`() {
        assertThrows(IllegalArgumentException::class.java) {
            AppEnvironment.from("STAGING")
        }
    }

    @Test
    fun `identifies debug and release environments`() {
        assertEquals(true, AppEnvironment.DEBUG.isDebug)
        assertEquals(false, AppEnvironment.QA.isDebug)
        assertEquals(true, AppEnvironment.RELEASE.isRelease)
        assertEquals(false, AppEnvironment.QA.isRelease)
    }
}
