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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

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

    private fun createViewModel(
        repository: RecordingSettingsRepository = RecordingSettingsRepository(),
        platformActions: FakeSettingsPlatformActions = FakeSettingsPlatformActions(),
        appEventBus: AppEventBus = AppEventBus(),
    ) = SettingsViewModel(
        repository = repository,
        downloadRepository = EmptyDownloadRepository,
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

private class RecordingSettingsRepository(
    initialSettings: AppSettings = AppSettings(),
) : SettingsRepository {
    override val settings: Flow<AppSettings> = flowOf(initialSettings)
    var useConfiguredDownloadLocation: Boolean? = null
    var downloadTreeUri: String? = null

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    ) = Unit

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit

    override suspend fun setDownloadLocation(
        enabled: Boolean,
        treeUri: String?,
    ) {
        useConfiguredDownloadLocation = enabled
        downloadTreeUri = treeUri
    }
}

private object EmptyNavigationDispatcher : NavigationDispatcher {
    override val events: Flow<NavigationEvent> = emptyFlow()

    override suspend fun dispatch(event: NavigationEvent) = Unit
}

private object EmptyDownloadRepository : DownloadRepository {
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

    override suspend fun refreshNetworkPolicy() = Unit
}

private const val DOWNLOAD_TREE_URI = "content://downloads/tree/videos"
private const val OLD_DOWNLOAD_TREE_URI = "content://downloads/tree/old-videos"
