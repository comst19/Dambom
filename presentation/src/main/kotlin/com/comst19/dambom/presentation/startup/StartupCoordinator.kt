package com.comst19.dambom.presentation.startup

import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.ui.locale.AppLanguagePreferences
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.coroutine.ApplicationScope
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.navigation.TopLevelNavKey
import com.comst19.dambom.feature.home.api.HomeKey
import com.comst19.dambom.presentation.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface StartupCoordinator {
    suspend fun initialize(): TopLevelNavKey
}

@Singleton
class DefaultStartupCoordinator
    @Inject
    constructor(
        private val downloadRepository: DownloadRepository,
        private val appLanguagePreferences: AppLanguagePreferences,
        private val appEventBus: AppEventBus,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) : StartupCoordinator {
        override suspend fun initialize(): TopLevelNavKey {
            applicationScope.launch {
                suspendRunCatching { downloadRepository.recoverPendingDownloads() }
                    .onFailure {
                        appEventBus.send(
                            AppEvent.ShowSnackbar(UiText.Resource(R.string.download_recovery_failed)),
                        )
                    }
            }
            suspendRunCatching { appLanguagePreferences.syncSystemLanguage() }
            return HomeKey
        }
    }
