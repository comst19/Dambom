package com.comst19.dambom.presentation.event

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.SnackbarDuration
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.presentation.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObserveAppEventsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `subsequent events use the current resource context without replacing the collector`() {
        val base = ApplicationProvider.getApplicationContext<Context>()

        fun localized(locale: Locale): Context =
            base.createConfigurationContext(
                Configuration(base.resources.configuration).apply { setLocale(locale) },
            )
        val english = localized(Locale.ENGLISH)
        val korean = localized(Locale.KOREAN)
        val context = mutableStateOf(english)
        val host = SnackbarHostState()
        val events = AppEventBus()
        val resource = R.string.download_recovery_failed
        assertNotEquals(english.getString(resource), korean.getString(resource))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context.value) { ObserveAppEvents(events, host) }
        }
        val event = AppEvent.ShowSnackbar(UiText.Resource(resource), SnackbarDuration.Indefinite)
        compose.runOnIdle { runBlocking { events.send(event) } }
        compose.waitUntil { host.currentSnackbarData != null }
        val first = checkNotNull(host.currentSnackbarData)
        assertEquals(english.getString(resource), first.visuals.message)
        compose.runOnIdle { context.value = korean }
        compose.waitForIdle()
        compose.runOnIdle { runBlocking { events.send(event) } }
        compose.waitUntil { host.currentSnackbarData != null && host.currentSnackbarData !== first }
        assertEquals(korean.getString(resource), host.currentSnackbarData?.visuals?.message)
    }
}
