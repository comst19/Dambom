package com.comst19.dambom.core.network

import com.comst19.dambom.core.network.model.ApiResponse
import com.comst19.dambom.core.network.model.unwrapData
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiResponseTest {
    @Test
    fun `unwrap data returns response payload`() {
        val response = ApiResponse(data = "payload")

        assertEquals("payload", response.unwrapData())
    }
}
