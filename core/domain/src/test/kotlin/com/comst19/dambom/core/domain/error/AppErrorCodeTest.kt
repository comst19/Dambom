package com.comst19.dambom.core.domain.error

import org.junit.Assert.assertEquals
import org.junit.Test

class AppErrorCodeTest {
    @Test
    fun `unknown server error code maps to unknown and remains forward compatible`() {
        assertEquals(AppErrorCode.UNKNOWN, AppErrorCode.from("NEW_SERVER_ERROR"))
        assertEquals(AppErrorCode.UNKNOWN, AppErrorCode.from(null))
    }
}
