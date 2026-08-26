package com.comst19.dambom.core.common.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedUrlBusTest {
    @Test
    fun `shared text extracts the first http url`() {
        val bus = SharedUrlBus()

        bus.offer("영상 링크 https://example.com/video.mp4 확인")

        assertEquals("https://example.com/video.mp4", bus.pendingUrl.value)
    }

    @Test
    fun `non url text is ignored`() {
        val bus = SharedUrlBus()

        bus.offer("링크가 없는 텍스트")

        assertNull(bus.pendingUrl.value)
    }
}
