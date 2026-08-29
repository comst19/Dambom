package com.comst19.dambom.presentation.startup

import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import com.comst19.dambom.core.navigation.contract.TopLevelNavKey
import com.comst19.dambom.presentation.R
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
        private val appEventBus: AppEventBus,
    ) : StartupCoordinator {
        override suspend fun initialize(): TopLevelNavKey {
            suspendRunCatching { downloadRepository.ensureDownloadsScheduled() }
                .onFailure {
                    appEventBus.send(
                        AppEvent.ShowSnackbar(UiText.Resource(R.string.download_recovery_failed)),
                    )
                }
            return HomeKey
        }
    }
