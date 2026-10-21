package com.comst19.dambom.feature.detection.component

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.domain.model.MediaCandidate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidatePreviewPlayerTest {
    @Test
    fun `preview applies the selected adaptive video height`() {
        val candidate =
            MediaCandidate(
                "video",
                "https://example.com/master.m3u8",
                "Video",
                null,
                null,
                videoHeight = 90,
            )
        val player = createCandidatePreviewPlayer(ApplicationProvider.getApplicationContext<Context>(), candidate)
        try {
            assertEquals(90, player.trackSelectionParameters.maxVideoHeight)
        } finally {
            player.release()
        }
    }

    @Test
    fun `preview seeks ten seconds in both directions`() {
        val player = createCandidatePreviewPlayer(ApplicationProvider.getApplicationContext<Context>())

        try {
            assertEquals(
                listOf(10_000L, 10_000L),
                listOf(player.seekBackIncrement, player.seekForwardIncrement),
            )
        } finally {
            player.release()
        }
    }
}
