package com.comst19.dambom.core.navigation.contract

import com.comst19.dambom.core.navigation.contract.AuthGraph.LoginKey
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppDestinationKeyTest {
    @Test
    fun `destination key serializer is generated`() {
        assertNotNull(LoginKey.serializer())
    }
}
