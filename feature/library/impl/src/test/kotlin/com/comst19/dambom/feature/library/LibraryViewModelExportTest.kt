package com.comst19.dambom.feature.library

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import com.comst19.dambom.core.domain.model.SharedLinkQuality
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.export.ExportDocumentStore
import com.comst19.dambom.feature.library.file.LibraryFileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class LibraryViewModelExportTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val createdFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        createdFiles.forEach(File::delete)
        createdFiles.clear()
        context.noBackupFilesDir.resolve("pending-exports").deleteRecursively()
    }

    @Test
    fun `exportRestored discards destination document and notifies failure when source task is missing`() =
        runTest(dispatcherRule.dispatcher) {
            val provider =
                FakeLibraryDocumentsProvider().apply {
                    attachInfo(
                        context,
                        ProviderInfo().apply {
                            authority = DOCUMENTS_AUTHORITY
                            exported = true
                        },
                    )
                }
            ShadowContentResolver.registerProviderInternal(DOCUMENTS_AUTHORITY, provider)
            val destination = DocumentsContract.buildDocumentUri(DOCUMENTS_AUTHORITY, "missing.mp4")
            val events = AppEventBus()
            val viewModel = createViewModel(tasks = emptyList(), events = events)

            viewModel.exportRestored("missing-id", destination.toString())
            runCurrent()

            assertEquals(listOf(destination), provider.deletedUris)
            assertEquals(
                AppEvent.ShowSnackbar(UiText.Resource(R.string.library_export_failure)),
                events.events.first(),
            )
        }

    @Test
    fun `exportToConfiguredLocation clears expired folder permission and reports validity`() =
        runTest(dispatcherRule.dispatcher) {
            val source = createSourceFile(byteArrayOf(1, 2, 3))
            val task = libraryTestVideo(source.path)
            val treeUri = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FDambom"
            val settings =
                RecordingSettingsRepository(
                    AppSettings(
                        useConfiguredDownloadLocation = true,
                        downloadTreeUri = treeUri,
                    ),
                )
            val events = AppEventBus()
            val viewModel = createViewModel(tasks = listOf(task), settings = settings, events = events)

            assertFalse(viewModel.hasValidConfiguredDownloadLocation(treeUri))
            assertTrue(viewModel.hasValidConfiguredDownloadLocation(null))

            viewModel.exportToConfiguredLocation(task)
            runCurrent()

            assertFalse(settings.settings.value.useConfiguredDownloadLocation)
            assertNull(settings.settings.value.downloadTreeUri)
            assertEquals(listOf(false to null), settings.downloadLocationUpdates)
            assertEquals(
                AppEvent.ShowSnackbar(UiText.Resource(R.string.library_export_location_permission_expired)),
                events.events.first(),
            )
        }

    @Test
    fun `exportToConfiguredLocation saves to default downloads when no tree uri is configured`() =
        runTest(dispatcherRule.dispatcher) {
            val payload = byteArrayOf(11, 22, 33, 44)
            val source = createSourceFile(payload)
            val task = libraryTestVideo(source.path)
            val insertedDestination = Uri.parse("content://media/external/downloads/42")
            val output = ByteArrayOutputStream()
            val mediaProvider =
                FakeMediaStoreProvider(insertedDestination).apply {
                    attachInfo(
                        context,
                        ProviderInfo().apply {
                            authority = MediaStore.AUTHORITY
                            exported = true
                        },
                    )
                }
            ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, mediaProvider)
            shadowOf(context.contentResolver).registerOutputStream(insertedDestination, output)
            val settings =
                RecordingSettingsRepository(
                    AppSettings(
                        useConfiguredDownloadLocation = true,
                        downloadTreeUri = null,
                    ),
                )
            val events = AppEventBus()
            val viewModel = createViewModel(tasks = listOf(task), settings = settings, events = events)

            viewModel.exportToConfiguredLocation(task)
            runCurrent()

            assertEquals(MediaStore.Downloads.EXTERNAL_CONTENT_URI, mediaProvider.lastInsertedCollection)
            assertEquals(insertedDestination, mediaProvider.lastUpdatedUri)
            assertEquals(0, mediaProvider.lastUpdatedValues?.getAsInteger(MediaStore.Video.Media.IS_PENDING))
            assertEquals(0, mediaProvider.deleteCalls)
            assertArrayEquals(payload, output.toByteArray())
            assertEquals(
                AppEvent.ShowSnackbar(UiText.Resource(R.string.library_export_success)),
                events.events.first(),
            )
        }

    private fun createSourceFile(bytes: ByteArray): File =
        File
            .createTempFile("library-export-test", ".mp4", context.cacheDir)
            .apply {
                writeBytes(bytes)
                createdFiles += this
            }

    private fun createViewModel(
        tasks: List<com.comst19.dambom.core.domain.model.DownloadTask>,
        settings: SettingsRepository = RecordingSettingsRepository(AppSettings()),
        events: AppEventBus = AppEventBus(),
    ): LibraryViewModel =
        LibraryViewModel(
            repository = LibraryTestDownloads(tasks),
            settingsRepository = settings,
            navigation = SpyNavigationDispatcher(),
            savedStateHandle = SavedStateHandle(),
            fileManager =
                LibraryFileManager(
                    context = context,
                    ioDispatcher = dispatcherRule.dispatcher,
                    documents = ExportDocumentStore(context, dispatcherRule.dispatcher),
                ),
            appEventBus = events,
            playbackHistoryRepository = LibraryTestPlaybackHistory(),
            privateVideoSession = PrivateVideoSession(),
            defaultDispatcher = dispatcherRule.dispatcher,
        )

    private companion object {
        const val DOCUMENTS_AUTHORITY = "com.comst19.dambom.test.library.export.documents"
    }
}

private const val METHOD_DELETE_DOCUMENT = "android:deleteDocument"
private const val EXTRA_URI = "uri"

private class RecordingSettingsRepository(
    initial: AppSettings,
) : SettingsRepository {
    override val settings = MutableStateFlow(initial)
    val downloadLocationUpdates = mutableListOf<Pair<Boolean, String?>>()

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun setPlaybackSpeed(speed: Float) = Unit

    override suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    ) = Unit

    override suspend fun setSharedLinkAutoDownload(enabled: Boolean) = Unit

    override suspend fun setSharedLinkQuality(
        quality: SharedLinkQuality,
        resolution: Int,
    ) = Unit

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit

    override suspend fun setDownloadLocation(
        enabled: Boolean,
        treeUri: String?,
    ) {
        downloadLocationUpdates += enabled to treeUri
        settings.value =
            settings.value.copy(
                useConfiguredDownloadLocation = enabled,
                downloadTreeUri = treeUri,
            )
    }
}

private class FakeLibraryDocumentsProvider : ContentProvider() {
    val deletedUris = mutableListOf<Uri>()

    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? =
        if (method == METHOD_DELETE_DOCUMENT) {
            extras?.getParcelable(EXTRA_URI, Uri::class.java)?.let(deletedUris::add)
            Bundle.EMPTY
        } else {
            super.call(method, arg, extras)
        }

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

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

private class FakeMediaStoreProvider(
    private val insertedDestination: Uri,
) : ContentProvider() {
    var lastInsertedCollection: Uri? = null
    var lastUpdatedUri: Uri? = null
    var lastUpdatedValues: ContentValues? = null
    var deleteCalls = 0

    override fun onCreate(): Boolean = true

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri {
        lastInsertedCollection = uri
        return insertedDestination
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        lastUpdatedUri = uri
        lastUpdatedValues = values?.let(::ContentValues)
        return 1
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        deleteCalls++
        return 1
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String = "video/mp4"
}
