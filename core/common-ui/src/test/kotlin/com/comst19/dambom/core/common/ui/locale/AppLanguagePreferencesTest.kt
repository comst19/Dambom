package com.comst19.dambom.core.common.ui.locale

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], qualifiers = "b+ks+IN")
class AppLanguagePreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var activity: ActivityController<LanguageTestActivity>

    @Before
    fun setUp() {
        context
            .getSharedPreferences("app_language", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        activity = Robolectric.buildActivity(LanguageTestActivity::class.java)
        activity.setup()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @After
    fun tearDown() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        activity.pause().stop().destroy()
    }

    @Test
    fun `automatic choice stays represented as system in settings`() {
        AppLanguagePreferences.syncSystemLanguage(context)

        assertEquals("hi", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        assertEquals("", AppLanguagePreferences.selectedLanguageTags(context))
        AppLanguagePreferences.syncSystemLanguage(context)
        assertEquals("", AppLanguagePreferences.selectedLanguageTags(context))
    }

    @Test
    fun `automatic selection follows a changed system locale on reentry`() {
        AppLanguagePreferences.syncSystemLanguage(context)
        assertEquals("hi", AppCompatDelegate.getApplicationLocales().toLanguageTags())

        RuntimeEnvironment.setQualifiers("de-rDE")
        AppLanguagePreferences.syncSystemLanguage(context)

        assertEquals("de", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        assertEquals("", AppLanguagePreferences.selectedLanguageTags(context))
    }

    @Test
    @Config(qualifiers = "pa-rPK")
    fun `regional script is inferred before matching the supported translation`() {
        AppLanguagePreferences.syncSystemLanguage(context)

        assertEquals("ur", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        assertEquals("", AppLanguagePreferences.selectedLanguageTags(context))
    }

    @Test
    fun `explicit choice survives automatic synchronization`() {
        AppLanguagePreferences.apply(context, "de")
        AppLanguagePreferences.syncSystemLanguage(context)

        assertEquals("de", AppLanguagePreferences.selectedLanguageTags(context))
        assertEquals("de", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    fun `existing explicit preference survives upgrade`() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ko"))
        AppLanguagePreferences.syncSystemLanguage(context)

        assertEquals("ko", AppLanguagePreferences.selectedLanguageTags(context))
    }

    @Test
    fun `returning to system removes explicit selection`() {
        AppLanguagePreferences.apply(context, "de")
        AppLanguagePreferences.apply(context, "")

        assertEquals("", AppLanguagePreferences.selectedLanguageTags(context))
        assertFalse(AppCompatDelegate.getApplicationLocales().isEmpty)
    }

    @Test
    fun `external app language change is kept`() {
        AppLanguagePreferences.apply(context, "")
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))
        AppLanguagePreferences.syncSystemLanguage(context)

        assertEquals("fr", AppLanguagePreferences.selectedLanguageTags(context))
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hi"))
        AppLanguagePreferences.syncSystemLanguage(context)
        assertEquals("hi", AppLanguagePreferences.selectedLanguageTags(context))
    }
}

class LanguageTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        super.onCreate(savedInstanceState)
    }
}
