package com.comst19.dambom.feature.library.navigation

import android.os.Bundle
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.comst19.dambom.feature.library.api.LibraryKey
import com.comst19.dambom.feature.library.api.VideoDetailKey
import kotlinx.serialization.PolymorphicSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryNavigationSerializersTest {
    private val configuration =
        SavedStateConfiguration {
            serializersModule = libraryNavSerializersModule
        }
    private val serializer = PolymorphicSerializer(NavKey::class)

    @Test
    fun `all public and internal library keys encode fixed serial names and decode from fixed fixtures`() {
        libraryKeyFixtures().forEach { case ->
            val encoded = encodeToSavedState(serializer, case.key, configuration)
            assertEquals(case.serialName, encoded.getString("type"))
            assertNotNull(encoded.getBundle("value"))
            assertEquals(case.key, decodeFromSavedState(serializer, encoded, configuration))

            val fixedFixture =
                Bundle().apply {
                    putString("type", case.serialName)
                    putBundle("value", case.fixtureValue)
                }
            assertEquals(case.key, decodeFromSavedState(serializer, fixedFixture, configuration))
        }
    }

    private fun libraryKeyFixtures(): List<FixtureCase> =
        listOf(
            FixtureCase("dambom.library", LibraryKey, Bundle()),
            FixtureCase(
                serialName = "dambom.video_detail",
                key = VideoDetailKey(id = "video-1", isPrivate = true),
                fixtureValue =
                    Bundle().apply {
                        putString("id", "video-1")
                        putBoolean("isPrivate", true)
                    },
            ),
            FixtureCase("dambom.private_library", PrivateLibraryKey, Bundle()),
            FixtureCase(
                serialName = "dambom.video_subtitle_transcript",
                key = VideoSubtitleTranscriptKey(id = "video-2"),
                fixtureValue = Bundle().apply { putString("id", "video-2") },
            ),
            FixtureCase(
                serialName = "dambom.video_subtitle",
                key = VideoSubtitleKey(id = "video-3"),
                fixtureValue = Bundle().apply { putString("id", "video-3") },
            ),
            FixtureCase(
                serialName = "dambom.video_subtitle_export",
                key = VideoSubtitleExportKey(id = "video-4"),
                fixtureValue = Bundle().apply { putString("id", "video-4") },
            ),
            FixtureCase(
                serialName = "dambom.video_compression",
                key = VideoCompressionKey(id = "video-5"),
                fixtureValue = Bundle().apply { putString("id", "video-5") },
            ),
            FixtureCase(
                serialName = "dambom.video_trim",
                key = VideoTrimKey(id = "video-6"),
                fixtureValue = Bundle().apply { putString("id", "video-6") },
            ),
        )

    private data class FixtureCase(
        val serialName: String,
        val key: NavKey,
        val fixtureValue: Bundle,
    )
}
