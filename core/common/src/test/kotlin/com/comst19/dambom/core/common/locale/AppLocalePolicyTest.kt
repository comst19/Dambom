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
        assertEquals("ko", resolve("ja-KR"))
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
        assertEquals("en", resolve("ja-JP", "fr-FR"))
    }

    @Test
    fun `unknown country or missing locale uses English`() {
        assertEquals("en", resolve("ja-JP"))
        assertEquals("en", resolve("ja"))
        assertEquals("en", resolve())
    }

    @Test
    fun `every selectable language resolves without its country`() {
        AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.forEach {
            assertEquals(it.languageTag, resolve(it.languageTag))
        }
    }

    private fun resolve(vararg tags: String): String = AppLocalePolicy.resolve(tags.map(Locale::forLanguageTag))
}
