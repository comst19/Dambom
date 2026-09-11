package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.DownloadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class VideoDetailStateTest {
    @Test
    fun `upstream cancellation propagates without an error state`() =
        runTest {
            val cancellation = CancellationException("Cancelled lookup")
            val states = mutableListOf<VideoDetailState>()
            try {
                flow<DownloadTask?> { throw cancellation }.asVideoDetailState().toList(states)
                fail("Cancellation should propagate")
            } catch (caught: CancellationException) {
                assertSame(cancellation, caught)
            }
            assertEquals(listOf(VideoDetailState.Loading), states)
        }

    @Test
    fun `first emission is loading then ready and deletion becomes not found`() =
        runTest {
            val video = savedVideo("video")
            val states = flowOf(video, null).asVideoDetailState().toList()

            assertEquals(
                listOf(VideoDetailState.Loading, VideoDetailState.Ready(video), VideoDetailState.NotFound),
                states,
            )
        }

    @Test
    fun `query failure is distinct from missing video`() =
        runTest {
            val states = flow<DownloadTask?> { throw IOException("Read failed") }.asVideoDetailState().toList()

            assertEquals(listOf(VideoDetailState.Loading, VideoDetailState.Error), states)
            assertEquals(
                listOf(VideoDetailState.Loading, VideoDetailState.NotFound),
                flowOf<DownloadTask?>(null).asVideoDetailState().toList(),
            )
        }
}
