package com.comst19.dambom.feature.settings

import android.content.res.Configuration
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.locale.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalizedResourcesTest {
    @Test
    fun `unsupported language falls back to English`() {
        assertEquals("Settings", title("ja-JP"))
        assertEquals("Settings", title("es-ES"))
    }

    @Test
    fun `regional locales select their language resources`() {
        assertEquals("설정", title("ko-KR"))
        assertEquals("Settings", title("en-NG"))
        assertEquals("Paramètres", title("fr-BJ"))
    }

    @Test
    fun `every supported language has a localized settings title`() {
        AppLanguage.entries.filter { it != AppLanguage.SYSTEM && it != AppLanguage.ENGLISH }.forEach {
            assertNotEquals(it.languageTag, "Settings", title(it.languageTag))
        }
    }

    @Test
    fun `right to left languages retain their layout direction`() {
        listOf("ar", "ur", "he", "fa", "ps", "sd", "ckb").forEach {
            val configuration = Configuration()
            configuration.setLocale(Locale.forLanguageTag(it))
            assertEquals(it, View.LAYOUT_DIRECTION_RTL, configuration.layoutDirection)
        }
    }

    private fun title(languageTag: String): String {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(languageTag))
        return context.createConfigurationContext(configuration).getString(R.string.settings_title)
    }
}
