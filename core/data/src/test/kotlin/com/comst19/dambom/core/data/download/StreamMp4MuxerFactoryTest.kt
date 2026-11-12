package com.comst19.dambom.core.data.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class StreamMp4MuxerFactoryTest {
    @Test
    fun mixedPrefixesKeepAllNalPayloadBytes() {
        val input = ByteBuffer.wrap("ff00000109f00000000168e9782b2c8b000001060501000001".hexToByteArray())
        input.position(1)
        val sampleSize = input.remaining() - 3

        val output = checkNotNull(normalizeAvcSample(input, sampleSize))
        val bytes = ByteArray(output.remaining())
        output.get(bytes)

        assertArrayEquals("0000000109f00000000168e9782b2c8b00000001060501".hexToByteArray(), bytes)
        assertEquals(1, input.position())
    }

    @Test
    fun fourBytePrefixesDoNotNeedCopying() {
        val input = ByteBuffer.wrap("0000000168e9782b2c8b00000001060501".hexToByteArray())

        assertNull(normalizeAvcSample(input, input.remaining()))
    }
}
