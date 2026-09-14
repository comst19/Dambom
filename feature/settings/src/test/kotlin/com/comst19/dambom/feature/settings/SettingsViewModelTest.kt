package com.comst19.dambom.feature.settings

import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.feature.settings.contract.AppLanguage
import com.comst19.dambom.feature.settings.contract.SaveLocationMode
import com.comst19.dambom.feature.settings.platform.SettingsPlatformActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `language state and platform locale stay in sync`() {
        val platform = FakeSettingsPlatformActions(currentLanguageTags = "ko-KR")
        val viewModel = createViewModel(platformActions = platform)

        assertEquals(AppLanguage.KOREAN, viewModel.language.value)

        viewModel.setLanguage(AppLanguage.ENGLISH)

        assertEquals(AppLanguage.ENGLISH, viewModel.language.value)
        assertEquals("en", platform.appliedLanguageTag)
    }

    @Test
    fun `persisted directory is saved in settings`() =
        runTest {
            val repository = RecordingSettingsRepository()
            val viewModel = createViewModel(repository = repository)

            viewModel.setDownloadDirectory(DOWNLOAD_TREE_URI)
            advanceUntilIdle()

            assertEquals(DOWNLOAD_TREE_URI, repository.downloadTreeUri)
        }

    @Test
    fun `save location mode maps to the repository flag`() =
        runTest {
            val repository = RecordingSettingsRepository()
            val viewModel = createViewModel(repository = repository)

            viewModel.setSaveLocationMode(SaveLocationMode.CHOOSE_EACH_TIME)
            advanceUntilIdle()

            assertEquals(false, repository.useConfiguredDownloadLocation)
        }

    @Test
    fun `rejected directory permission reports failure without saving`() =
        runTest {
            val repository = RecordingSettingsRepository()
            val events = AppEventBus()
            val viewModel =
                createViewModel(
                    repository = repository,
                    platformActions = FakeSettingsPlatformActions(canPersistDirectory = false),
                    appEventBus = events,
                )

            viewModel.setDownloadDirectory(DOWNLOAD_TREE_URI)
            advanceUntilIdle()

            assertNull(repository.downloadTreeUri)
            assertEquals(
                R.string.settings_download_location_failure,
                ((events.events.first() as AppEvent.ShowSnackbar).message as UiText.Resource).id,
            )
        }

    @Test
    fun `replacement directory releases the old grant after the new location is saved`() =
        runTest {
            val repository =
                RecordingSettingsRepository(
                    initialSettings = AppSettings(downloadTreeUri = OLD_DOWNLOAD_TREE_URI),
                )
            val platform = FakeSettingsPlatformActions()
            val viewModel = createViewModel(repository = repository, platformActions = platform)

            viewModel.setDownloadDirectory(DOWNLOAD_TREE_URI)
            advanceUntilIdle()

            assertEquals(
                listOf("take:$DOWNLOAD_TREE_URI", "release:$OLD_DOWNLOAD_TREE_URI"),
                platform.calls,
            )
            assertEquals(DOWNLOAD_TREE_URI, repository.downloadTreeUri)
        }

    @Test
    fun `theme persistence failure reports feedback`() =
        runTest {
            val repository = RecordingSettingsRepository(themeFailure = IOException("theme"))
            val events = AppEventBus()
            val received = mutableListOf<AppEvent>()
            events.events.onEach(received::add).launchIn(backgroundScope)
            val viewModel = createViewModel(repository = repository, appEventBus = events)

            viewModel.setThemeMode(ThemeMode.DARK)
            advanceUntilIdle()

            assertEquals(listOf(R.string.settings_update_failure), received.resourceIds())
        }

    @Test
    fun `clipboard persistence failure reports feedback`() =
        runTest {
            val repository = RecordingSettingsRepository(clipboardFailure = IOException("clipboard"))
            val events = AppEventBus()
            val received = mutableListOf<AppEvent>()
            events.events.onEach(received::add).launchIn(backgroundScope)
            val viewModel = createViewModel(repository = repository, appEventBus = events)

            viewModel.setClipboardSuggestion(true)
            advanceUntilIdle()

            assertEquals(listOf(R.string.settings_update_failure), received.resourceIds())
        }

    @Test
    fun `save mode persistence failure reports feedback`() =
        runTest {
            val repository = RecordingSettingsRepository(downloadLocationFailure = IOException("mode"))
            val events = AppEventBus()
            val received = mutableListOf<AppEvent>()
            events.events.onEach(received::add).launchIn(backgroundScope)
            val viewModel = createViewModel(repository = repository, appEventBus = events)

            viewModel.setSaveLocationMode(SaveLocationMode.CHOOSE_EACH_TIME)
            advanceUntilIdle()

            assertEquals(listOf(R.string.settings_update_failure), received.resourceIds())
        }

    @Test
    fun `wifi persistence failure skips policy refresh and reports update failure`() =
        runTest {
            val calls = mutableListOf<String>()
            val repository =
                RecordingSettingsRepository(
                    wifiFailure = IOException("wifi"),
                    calls = calls,
                )
            val downloadRepository = RecordingDownloadRepository(calls = calls)
            val events = AppEventBus()
            val received = mutableListOf<AppEvent>()
            events.events.onEach(received::add).launchIn(backgroundScope)
            val viewModel =
                createViewModel(
                    repository = repository,
                    downloadRepository = downloadRepository,
                    appEventBus = events,
                )

            viewModel.setWifiOnlyDownloads(false)
            advanceUntilIdle()

            assertEquals(listOf("saveWifi:false"), calls)
            assertEquals(listOf(R.string.settings_update_failure), received.resourceIds())
        }

    @Test
    fun `wifi policy refresh failure is distinguished after the setting is saved`() =
        runTest {
            val calls = mutableListOf<String>()
            val repository = RecordingSettingsRepository(calls = calls)
            val downloadRepository =
                RecordingDownloadRepository(
                    refreshFailure = IOException("refresh"),
                    calls = calls,
                )
            val events = AppEventBus()
            val received = mutableListOf<AppEvent>()
            events.events.onEach(received::add).launchIn(backgroundScope)
            val viewModel =
                createViewModel(
                    repository = repository,
                    downloadRepository = downloadRepository,
                    appEventBus = events,
                )

            viewModel.setWifiOnlyDownloads(false)
            advanceUntilIdle()

            assertEquals(listOf("saveWifi:false", "refresh"), calls)
            assertEquals(false, repository.currentSettings.wifiOnlyDownloads)
            assertEquals(listOf(R.string.settings_wifi_policy_apply_failure), received.resourceIds())
        }

    @Test
    fun `settings update cancellation has no failure feedback`() =
        runTest {
            val repository = RecordingSettingsRepository(themeFailure = CancellationException("cancelled"))
            val events = AppEventBus()
            val received = mutableListOf<AppEvent>()
            events.events.onEach(received::add).launchIn(backgroundScope)
            val viewModel = createViewModel(repository = repository, appEventBus = events)

            viewModel.setThemeMode(ThemeMode.DARK)
            advanceUntilIdle()

            assertEquals(emptyList<Int>(), received.resourceIds())
        }

    @Test
    fun `same directory save failure preserves the existing grant`() =
        runTest {
            val repository =
                RecordingSettingsRepository(
                    initialSettings = AppSettings(downloadTreeUri = DOWNLOAD_TREE_URI),
                    downloadLocationFailure = IOException("save"),
                )
            val platform = FakeSettingsPlatformActions()
            val viewModel = createViewModel(repository = repository, platformActions = platform)

            viewModel.setDownloadDirectory(DOWNLOAD_TREE_URI)
            advanceUntilIdle()

            assertEquals(listOf("take:$DOWNLOAD_TREE_URI"), platform.calls)
            assertEquals(DOWNLOAD_TREE_URI, repository.currentSettings.downloadTreeUri)
        }

    @Test
    fun `failed replacement releases only the newly acquired grant`() =
        runTest {
            val repository =
                RecordingSettingsRepository(
                    initialSettings = AppSettings(downloadTreeUri = OLD_DOWNLOAD_TREE_URI),
                    downloadLocationFailure = IOException("save"),
                )
            val platform = FakeSettingsPlatformActions()
            val viewModel = createViewModel(repository = repository, platformActions = platform)

            viewModel.setDownloadDirectory(DOWNLOAD_TREE_URI)
            advanceUntilIdle()

            assertEquals(
                listOf("take:$DOWNLOAD_TREE_URI", "release:$DOWNLOAD_TREE_URI"),
                platform.calls,
            )
            assertEquals(OLD_DOWNLOAD_TREE_URI, repository.currentSettings.downloadTreeUri)
        }

    @Test
    fun `same directory cancellation preserves the existing grant and has no feedback`() =
        runTest {
            val repository =
                RecordingSettingsRepository(
                    initialSettings = AppSettings(downloadTreeUri = DOWNLOAD_TREE_URI),
                    downloadLocationFailure = CancellationException("cancelled"),
                )
            val platform = FakeSettingsPlatformActions()
            val events = AppEventBus()
            val received = mutableListOf<AppEvent>()
            events.events.onEach(received::add).launchIn(backgroundScope)
            val viewModel =
                createViewModel(
                    repository = repository,
                    platformActions = platform,
                    appEventBus = events,
                )

            viewModel.setDownloadDirectory(DOWNLOAD_TREE_URI)
            advanceUntilIdle()

            assertEquals(listOf("take:$DOWNLOAD_TREE_URI"), platform.calls)
            assertEquals(emptyList<Int>(), received.resourceIds())
        }

    @Test
    fun `replacement directory cancellation releases only the newly acquired grant`() =
        runTest {
            val repository =
                RecordingSettingsRepository(
                    initialSettings = AppSettings(downloadTreeUri = OLD_DOWNLOAD_TREE_URI),
                    downloadLocationFailure = CancellationException("cancelled"),
                )
            val platform = FakeSettingsPlatformActions()
            val events = AppEventBus()
            val received = mutableListOf<AppEvent>()
            events.events.onEach(received::add).launchIn(backgroundScope)
            val viewModel =
                createViewModel(
                    repository = repository,
                    platformActions = platform,
                    appEventBus = events,
                )

            viewModel.setDownloadDirectory(DOWNLOAD_TREE_URI)
            advanceUntilIdle()

            assertEquals(
                listOf("take:$DOWNLOAD_TREE_URI", "release:$DOWNLOAD_TREE_URI"),
                platform.calls,
            )
            assertEquals(OLD_DOWNLOAD_TREE_URI, repository.currentSettings.downloadTreeUri)
            assertEquals(emptyList<Int>(), received.resourceIds())
        }

    @Test
    fun `overlapping directory changes release every superseded grant in order`() =
        runTest {
            val firstWriteStarted = CompletableDeferred<Unit>()
            val firstWriteGate = CompletableDeferred<Unit>()
            val repository =
                RecordingSettingsRepository(
                    initialSettings = AppSettings(downloadTreeUri = OLD_DOWNLOAD_TREE_URI),
                    firstDownloadLocationStarted = firstWriteStarted,
                    firstDownloadLocationGate = firstWriteGate,
                )
            val platform = FakeSettingsPlatformActions()
            val viewModel = createViewModel(repository = repository, platformActions = platform)

            viewModel.setDownloadDirectory(DOWNLOAD_TREE_URI)
            firstWriteStarted.await()
            viewModel.setDownloadDirectory(SECOND_DOWNLOAD_TREE_URI)
            runCurrent()

            assertEquals(listOf("take:$DOWNLOAD_TREE_URI"), platform.calls)

            firstWriteGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "take:$DOWNLOAD_TREE_URI",
                    "release:$OLD_DOWNLOAD_TREE_URI",
                    "take:$SECOND_DOWNLOAD_TREE_URI",
                    "release:$DOWNLOAD_TREE_URI",
                ),
                platform.calls,
            )
            assertEquals(SECOND_DOWNLOAD_TREE_URI, repository.currentSettings.downloadTreeUri)
        }

    @Test
    fun `save mode change waits for directory replacement and keeps the latest URI`() =
        runTest {
            val firstWriteStarted = CompletableDeferred<Unit>()
            val firstWriteGate = CompletableDeferred<Unit>()
            val repository =
                RecordingSettingsRepository(
                    initialSettings = AppSettings(downloadTreeUri = OLD_DOWNLOAD_TREE_URI),
                    firstDownloadLocationStarted = firstWriteStarted,
                    firstDownloadLocationGate = firstWriteGate,
                )
            val viewModel = createViewModel(repository = repository)

            viewModel.setDownloadDirectory(DOWNLOAD_TREE_URI)
            firstWriteStarted.await()
            viewModel.setSaveLocationMode(SaveLocationMode.CHOOSE_EACH_TIME)
            runCurrent()

            assertEquals(listOf("saveLocation:true:$DOWNLOAD_TREE_URI"), repository.calls)

            firstWriteGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "saveLocation:true:$DOWNLOAD_TREE_URI",
                    "saveLocation:false:$DOWNLOAD_TREE_URI",
                ),
                repository.calls,
            )
            assertEquals(DOWNLOAD_TREE_URI, repository.currentSettings.downloadTreeUri)
            assertEquals(false, repository.currentSettings.useConfiguredDownloadLocation)
        }

    private fun createViewModel(
        repository: RecordingSettingsRepository = RecordingSettingsRepository(),
        downloadRepository: DownloadRepository = RecordingDownloadRepository(),
        platformActions: FakeSettingsPlatformActions = FakeSettingsPlatformActions(),
        appEventBus: AppEventBus = AppEventBus(),
    ) = SettingsViewModel(
        repository = repository,
        downloadRepository = downloadRepository,
        navigation = EmptyNavigationDispatcher,
        appEventBus = appEventBus,
        platformActions = platformActions,
    )
}

private class FakeSettingsPlatformActions(
    override val currentLanguageTags: String = "",
    override val versionName: String = "1.0.1",
    private val canPersistDirectory: Boolean = true,
) : SettingsPlatformActions {
    var appliedLanguageTag: String? = null
    val calls = mutableListOf<String>()

    override fun applyLanguage(languageTag: String) {
        appliedLanguageTag = languageTag
    }

    override fun takePersistedDownloadDirectory(treeUri: String): Boolean {
        calls += "take:$treeUri"
        return canPersistDirectory
    }

    override fun releasePersistedDownloadDirectory(treeUri: String) {
        calls += "release:$treeUri"
    }
}

@Suppress("LongParameterList")
private class RecordingSettingsRepository(
    initialSettings: AppSettings = AppSettings(),
    private val themeFailure: Throwable? = null,
    private val clipboardFailure: Throwable? = null,
    private val wifiFailure: Throwable? = null,
    private val downloadLocationFailure: Throwable? = null,
    private val firstDownloadLocationStarted: CompletableDeferred<Unit>? = null,
    private val firstDownloadLocationGate: CompletableDeferred<Unit>? = null,
    val calls: MutableList<String> = mutableListOf(),
) : SettingsRepository {
    private val mutableSettings = MutableStateFlow(initialSettings)
    override val settings: Flow<AppSettings> = mutableSettings
    val currentSettings: AppSettings get() = mutableSettings.value
    var useConfiguredDownloadLocation: Boolean? = null
    var downloadTreeUri: String? = null
    private var downloadLocationCallCount = 0

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeFailure?.let { throw it }
        mutableSettings.value = mutableSettings.value.copy(themeMode = mode)
    }

    override suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    ) {
        clipboardFailure?.let { throw it }
        mutableSettings.value =
            mutableSettings.value.copy(
                clipboardPromptShown = promptShown,
                clipboardSuggestionEnabled = enabled,
            )
    }

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        calls += "saveWifi:$enabled"
        wifiFailure?.let { throw it }
        mutableSettings.value = mutableSettings.value.copy(wifiOnlyDownloads = enabled)
    }

    override suspend fun setDownloadLocation(
        enabled: Boolean,
        treeUri: String?,
    ) {
        calls += "saveLocation:$enabled:$treeUri"
        if (downloadLocationCallCount++ == 0) {
            firstDownloadLocationStarted?.complete(Unit)
            firstDownloadLocationGate?.await()
        }
        downloadLocationFailure?.let { throw it }
        useConfiguredDownloadLocation = enabled
        downloadTreeUri = treeUri
        mutableSettings.value =
            mutableSettings.value.copy(
                useConfiguredDownloadLocation = enabled,
                downloadTreeUri = treeUri,
            )
    }
}

private object EmptyNavigationDispatcher : NavigationDispatcher {
    override val events: Flow<NavigationEvent> = emptyFlow()

    override suspend fun dispatch(event: NavigationEvent) = Unit
}

private class RecordingDownloadRepository(
    private val refreshFailure: Throwable? = null,
    private val calls: MutableList<String> = mutableListOf(),
) : DownloadRepository {
    override val downloads: Flow<List<DownloadTask>> = flowOf(emptyList())

    override suspend fun enqueue(requests: List<DownloadRequest>) = EnqueueDownloadsResult(0, 0)

    override suspend fun pause(id: String) = Unit

    override suspend fun resume(id: String) = Unit

    override suspend fun cancel(id: String) = Unit

    override suspend fun rename(
        id: String,
        title: String,
    ) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun retry(id: String) = Unit

    override suspend fun pauseAll() = Unit

    override suspend fun resumeAll() = Unit

    override suspend fun recoverPendingDownloads() = Unit

    override suspend fun refreshNetworkPolicy() {
        calls += "refresh"
        refreshFailure?.let { throw it }
    }
}

private fun List<AppEvent>.resourceIds(): List<Int> =
    map { event ->
        ((event as AppEvent.ShowSnackbar).message as UiText.Resource).id
    }

private const val DOWNLOAD_TREE_URI = "content://downloads/tree/videos"
private const val OLD_DOWNLOAD_TREE_URI = "content://downloads/tree/old-videos"
private const val SECOND_DOWNLOAD_TREE_URI = "content://downloads/tree/second-videos"
