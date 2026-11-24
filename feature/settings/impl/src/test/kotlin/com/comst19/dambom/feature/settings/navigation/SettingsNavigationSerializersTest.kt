package com.comst19.dambom.feature.settings.navigation

import android.os.Bundle
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.comst19.dambom.feature.settings.api.SettingsKey
import kotlinx.serialization.PolymorphicSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsNavigationSerializersTest {
    private val configuration =
        SavedStateConfiguration {
            serializersModule = settingsNavSerializersModule
        }
    private val serializer = PolymorphicSerializer(NavKey::class)

    @Test
    fun `public and internal settings keys encode fixed serial names and decode from fixed fixtures`() {
        val cases =
            listOf(
                "dambom.settings" to SettingsKey,
                "dambom.help" to HelpKey,
            )

        cases.forEach { (serialName, key) ->
            val encoded = encodeToSavedState(serializer, key, configuration)
            assertEquals(serialName, encoded.getString("type"))
            assertNotNull(encoded.getBundle("value"))
            assertEquals(key, decodeFromSavedState(serializer, encoded, configuration))

            val fixedFixture =
                Bundle().apply {
                    putString("type", serialName)
                    putBundle("value", Bundle())
                }
            assertEquals(key, decodeFromSavedState(serializer, fixedFixture, configuration))
        }
    }
}
