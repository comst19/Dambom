package com.comst19.dambom.presentation.entry

import android.content.Intent
import com.comst19.dambom.core.common.url.SharedUrlBus
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class SharedTextEntryHandlerTest {
    @Test
    fun `text share navigates home and offers the first URL`() =
        runTest {
            val dispatcher = RecordingNavigationDispatcher()
            val sharedUrlBus = SharedUrlBus()
            val handler = SharedTextEntryHandler(dispatcher, sharedUrlBus)
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Watch https://example.com/video now")
                }

            handler.handle(intent)

            assertEquals(listOf(NavigationEvent.NavigateTopLevel(HomeKey)), dispatcher.dispatched)
            assertEquals("https://example.com/video", sharedUrlBus.pendingUrl.value)
        }

    @Test
    fun `non text share is ignored`() =
        runTest {
            val dispatcher = RecordingNavigationDispatcher()
            val sharedUrlBus = SharedUrlBus()
            val handler = SharedTextEntryHandler(dispatcher, sharedUrlBus)
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_TEXT, "https://example.com/video")
                }

            handler.handle(intent)

            assertEquals(emptyList<NavigationEvent>(), dispatcher.dispatched)
            assertNull(sharedUrlBus.pendingUrl.value)
        }
}

private class RecordingNavigationDispatcher : NavigationDispatcher {
    val dispatched = mutableListOf<NavigationEvent>()
    override val events: Flow<NavigationEvent> = emptyFlow()

    override suspend fun dispatch(event: NavigationEvent) {
        dispatched += event
    }
}
