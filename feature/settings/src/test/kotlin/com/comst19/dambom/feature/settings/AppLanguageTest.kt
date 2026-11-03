package com.comst19.dambom.feature.settings

import com.comst19.dambom.core.common.locale.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `locale tags map to supported app languages`() {
        assertEquals(AppLanguage.KOREAN, AppLanguage.from("ko-KR"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.from("en-US"))
    }

    @Test
    fun `all supported tags round trip including regional variants`() {
        AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.forEach {
            assertEquals(it, AppLanguage.from(it.languageTag))
            assertEquals(it, AppLanguage.from("${it.languageTag}-ZZ"))
        }
    }

    @Test
    fun `language tags match complete language subtags`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.from("hil-PH"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.from("en-US,ko-KR"))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.from("in-ID"))
        assertEquals(AppLanguage.HEBREW, AppLanguage.from("iw-IL"))
    }

    @Test
    fun `empty or unsupported locale tags use system default`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.from(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.from("ja-JP"))
    }
}
