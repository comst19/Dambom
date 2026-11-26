package com.comst19.dambom.feature.library.export

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.testing.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ExportDocumentStoreTest {
    @get:Rule val main = MainDispatcherRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var documentFile: File
    private lateinit var provider: FakeExportDocumentsProvider
    private lateinit var destination: Uri

    @Before
    fun setUp() {
        documentFile = File.createTempFile("export-doc", ".mp4", context.cacheDir)
        provider =
            FakeExportDocumentsProvider(documentFile).apply {
                attachInfo(
                    context,
                    ProviderInfo().apply {
                        authority = AUTHORITY
                        exported = true
                    },
                )
            }
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        destination = DocumentsContract.buildDocumentUri(AUTHORITY, "clip.mp4")
    }

    @After
    fun tearDown() {
        documentFile.delete()
    }

    @Test
    fun `canceling before io dispatch starts discards the empty document once`() =
        runTest(main.dispatcher) {
            val ioDispatcher = StandardTestDispatcher(testScheduler)
            val store = ExportDocumentStore(context, ioDispatcher)
            var transformCalls = 0
            var caughtCancellation = false

            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        store.save(
                            destination = destination,
                            transform = {
                                transformCalls++
                                it.writeBytes(byteArrayOf(1, 2, 3))
                            },
                            source = { it },
                        )
                    } catch (cancelled: CancellationException) {
                        caughtCancellation = true
                        throw cancelled
                    }
                }

            assertTrue(documentFile.exists())
            assertEquals(0, transformCalls)

            job.cancel()
            advanceUntilIdle()
            job.join()

            assertTrue(caughtCancellation)
            assertEquals(0, transformCalls)
            assertEquals(1, provider.deleteCalls)
            assertFalse(documentFile.exists())
        }

    @Test
    fun `canceling after entering io cleans up document exactly once`() =
        runTest(main.dispatcher) {
            val ioDispatcher = StandardTestDispatcher(testScheduler)
            val store = ExportDocumentStore(context, ioDispatcher)
            val enteredTransform = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()

            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    store.save(
                        destination = destination,
                        transform = {
                            enteredTransform.complete(Unit)
                            gate.await()
                        },
                        source = { it },
                    )
                }

            runCurrent()
            enteredTransform.await()
            job.cancel()
            advanceUntilIdle()
            job.join()

            assertEquals(1, provider.deleteCalls)
            assertFalse(documentFile.exists())
        }

    @Test
    fun `completing save writes output without deleting document`() =
        runTest(main.dispatcher) {
            val ioDispatcher = StandardTestDispatcher(testScheduler)
            val store = ExportDocumentStore(context, ioDispatcher)
            val payload = byteArrayOf(7, 8, 9, 10)
            val output = ByteArrayOutputStream()
            shadowOf(context.contentResolver).registerOutputStream(destination, output)

            val savedBytes =
                store.save(
                    destination = destination,
                    transform = { it.writeBytes(payload) },
                    source = { it },
                )

            assertEquals(payload.size.toLong(), savedBytes)
            assertEquals(0, provider.deleteCalls)
            assertTrue(documentFile.exists())
            assertArrayEquals(payload, output.toByteArray())
        }

    private class FakeExportDocumentsProvider(
        private val backingFile: File,
    ) : ContentProvider() {
        var deleteCalls = 0

        override fun onCreate(): Boolean = true

        override fun call(
            method: String,
            arg: String?,
            extras: Bundle?,
        ): Bundle? =
            if (method == METHOD_DELETE_DOCUMENT) {
                deleteCalls++
                if (backingFile.delete() || !backingFile.exists()) Bundle.EMPTY else null
            } else {
                super.call(method, arg, extras)
            }

        override fun openFile(
            uri: Uri,
            mode: String,
        ): ParcelFileDescriptor {
            val access = ParcelFileDescriptor.parseMode(mode)
            return ParcelFileDescriptor.open(backingFile, access)
        }

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String = "video/mp4"

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private companion object {
        const val AUTHORITY = "com.comst19.dambom.test.export.documents"
        const val METHOD_DELETE_DOCUMENT = "android:deleteDocument"
    }
}
