package com.comst19.dambom.core.common.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLocalePolicyTest {
    @Test
    fun `supported language wins over country default`() {
        assertEquals("pa", resolve("pa-IN"))
        assertEquals("en", resolve("en-PK"))
        assertEquals("fr", resolve("fr-BJ"))
        assertEquals("pt", resolve("pt-MZ"))
        assertEquals("ckb", resolve("ckb-IQ"))
    }

    @Test
    fun `unsupported language uses country default`() {
        assertEquals("hi", resolve("ks-IN"))
        assertEquals("ur", resolve("bal-PK"))
        assertEquals("fr", resolve("fon-BJ"))
        assertEquals("fr", resolve("swb-YT"))
        assertEquals("ar", resolve("tzm-MA"))
        assertEquals("pt", resolve("vmw-MZ"))
        assertEquals("ko", resolve("xx-KR"))
    }

    @Test
    fun `unsupported writing system uses the country default`() {
        assertEquals("pa", resolve("pa-Guru-IN"))
        assertEquals("ur", resolve("pa-Arab-PK"))
        assertEquals("hi", resolve("sd-Deva-IN"))
        assertEquals("fa", resolve("az-Arab-IR"))
        assertEquals("fa", resolve("uz-Arab-AF"))
    }

    @Test
    fun `primary system locale country wins before secondary languages`() {
        assertEquals("hi", resolve("ks-IN", "fr-FR"))
        assertEquals("en", resolve("xx-ZZ", "fr-FR"))
    }

    @Test
    fun `unknown country or missing locale uses English`() {
        assertEquals("en", resolve("xx-ZZ"))
        assertEquals("en", resolve("xx"))
        assertEquals("en", resolve())
    }

    @Test
    fun `every selectable language resolves without its country`() {
        AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.forEach {
            assertEquals(it.languageTag, resolve(it.languageTag))
        }
    }

    @Test
    fun `Chinese script and region select the matching translation`() {
        assertEquals("zh-Hans", resolve("zh"))
        assertEquals("zh-Hans", resolve("zh-CN"))
        assertEquals("zh-Hans", resolve("zh-SG"))
        assertEquals("zh-Hant", resolve("zh-TW"))
        assertEquals("zh-Hant", resolve("zh-HK"))
        assertEquals("zh-Hant", resolve("zh-MO"))
        assertEquals("zh-Hans", resolve("zh-Hans-TW"))
        assertEquals("zh-Hant", resolve("zh-Hant-CN"))
        assertEquals("zh-Hans", resolve("xx-CN"))
        assertEquals("zh-Hant", resolve("xx-TW"))
        assertEquals("ja", resolve("xx-JP"))
        assertEquals("es", resolve("xx-ES"))
        assertEquals("es", resolve("xx-MX"))
        assertEquals("ja", resolve("ja-JP"))
        assertEquals("es", resolve("es-MX"))
    }

    private fun resolve(vararg tags: String): String = AppLocalePolicy.resolve(tags.map(Locale::forLanguageTag))
}
