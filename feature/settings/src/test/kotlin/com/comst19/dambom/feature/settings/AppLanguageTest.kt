package com.comst19.dambom.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `locale tags map to supported app languages`() {
        assertEquals(AppLanguage.KOREAN, AppLanguage.from("ko-KR"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.from("en-US"))
    }

    @Test
    fun `empty or unsupported locale tags use system default`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.from(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.from("ja-JP"))
    }
}
