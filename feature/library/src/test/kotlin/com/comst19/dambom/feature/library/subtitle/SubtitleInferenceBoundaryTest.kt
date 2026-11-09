package com.comst19.dambom.feature.library.subtitle

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.feature.library.subtitle.data.GemmaModelStore
import com.comst19.dambom.feature.library.subtitle.inference.GemmaSubtitleEngine
import com.comst19.dambom.feature.library.subtitle.inference.PcmChunk
import com.comst19.dambom.feature.library.subtitle.inference.VideoAudioDecoder
import com.comst19.dambom.feature.library.subtitle.runtime.SubtitleRuntimeDelivery
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleInferenceBoundaryTest {
    @get:Rule val main = MainDispatcherRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `creating the model store does not touch its storage directory`() =
        runTest(main.dispatcher) {
            var reads = 0
            val wrapper =
                object : ContextWrapper(context) {
                    override fun getNoBackupFilesDir(): File {
                        reads++
                        return super.getNoBackupFilesDir()
                    }
                }
            val models = GemmaModelStore(wrapper, main.dispatcher)
            assertEquals(0, reads)
            models.installed()
            assertEquals(1, reads)
        }

    @Test fun `a timed out silent chunk does not prevent recognition of the next chunk`() =
        runTest(main.dispatcher) {
            val engine = engine()
            val silent = engine.transcribe(PcmChunk(0, 1_000, ByteArray(64))) { flow { awaitCancellation() } }
            val spoken =
                engine.transcribe(PcmChunk(1_000, 2_000, ByteArray(64))) {
                    flow { emit(finalResponse()) }
                }
            assertTrue(silent.isEmpty())
            assertEquals("hello", spoken.single().text)
            assertEquals(1_000L, spoken.single().startMillis)
        }

    @Test fun `timeout keeps finalized words without treating unfinished recognition as a fatal error`() =
        runTest(main.dispatcher) {
            val cues =
                engine().transcribe(PcmChunk(0, 1_000, ByteArray(64))) {
                    flow {
                        emit(finalResponse())
                        awaitCancellation()
                    }
                }
            assertEquals("hello", cues.single().text)
        }

    @Test fun `no speech response is an empty result while engine failures still propagate`() =
        runTest(main.dispatcher) {
            val engine = engine()
            val noSpeech =
                engine.transcribe(PcmChunk(0, 1_000, ByteArray(64))) {
                    flow {
                        throw GenAiException("ERROR_TYPE_NO_SPEECH_DETECTED", null, GenAiException.ErrorCode.UNKNOWN)
                    }
                }
            assertTrue(noSpeech.isEmpty())
            val failure = GenAiException("Service disconnected", null, GenAiException.ErrorCode.UNKNOWN)
            val result =
                runCatching {
                    engine.transcribe(PcmChunk(1_000, 2_000, ByteArray(64))) { flow { throw failure } }
                }
            assertEquals(failure, result.exceptionOrNull())
        }

    @Test fun `user cancellation still cancels recognition`() =
        runTest(main.dispatcher) {
            val started = CompletableDeferred<Unit>()
            val task =
                async {
                    engine().transcribe(PcmChunk(0, 1_000, ByteArray(64))) {
                        flow {
                            started.complete(Unit)
                            awaitCancellation()
                        }
                    }
                }
            started.await()
            task.cancelAndJoin()
            assertTrue(task.isCancelled)
        }

    private fun engine() =
        GemmaSubtitleEngine(
            context,
            main.dispatcher,
            VideoAudioDecoder(main.dispatcher),
            GemmaModelStore(context, main.dispatcher),
            SubtitleRuntimeDelivery(context),
        )

    private fun finalResponse() =
        SpeechRecognizerResponse.FinalTextResponse(
            "hello",
            listOf(SpeechRecognizerResponse.WordTiming("hello", 0)),
        )
}
