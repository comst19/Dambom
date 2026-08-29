package com.comst19.dambom.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.SnackbarDuration
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.error.AppDecodingException
import com.comst19.dambom.core.domain.error.AppErrorCode
import com.comst19.dambom.core.domain.error.AppNetworkException
import com.comst19.dambom.core.domain.error.AppRequestException
import com.comst19.dambom.core.domain.error.ErrorHandler
import com.comst19.dambom.core.domain.error.NetworkFailureReason
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.NetworkMonitor
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.presentation.contract.AppStartupState
import com.comst19.dambom.presentation.contract.StartupFailure
import com.comst19.dambom.presentation.startup.StartupCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        repository: SettingsRepository,
        networkMonitor: NetworkMonitor,
        downloadRepository: DownloadRepository,
        private val startupCoordinator: StartupCoordinator,
        private val errorHandler: ErrorHandler,
        private val appEventBus: AppEventBus,
    ) : ViewModel() {
        private val _startupState = MutableStateFlow<AppStartupState>(AppStartupState.Initializing)
        val startupState: StateFlow<AppStartupState> = _startupState.asStateFlow()

        val settings: StateFlow<AppSettings> =
            repository.settings.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SETTINGS_STOP_TIMEOUT_MILLIS),
                initialValue = AppSettings(),
            )

        val networkAccess: StateFlow<NetworkAccessState> =
            combine(networkMonitor.connection, settings) { connection, settings ->
                NetworkAccessState(connection, settings.wifiOnlyDownloads)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SETTINGS_STOP_TIMEOUT_MILLIS),
                initialValue = NetworkAccessState(),
            )

        init {
            initializeStartup()
            observeUnhandledErrors()
            observeDownloadFeedback(downloadRepository)
        }

        private fun initializeStartup() {
            viewModelScope.launch {
                suspendRunCatching(startupCoordinator::initialize).fold(
                    onSuccess = { startKey ->
                        _startupState.value = AppStartupState.Ready(startKey)
                    },
                    onFailure = {
                        _startupState.value = AppStartupState.Failed(StartupFailure.InitializationFailed)
                    },
                )
            }
        }

        private fun observeUnhandledErrors() {
            viewModelScope.launch {
                errorHandler.errors.collect(::handleUnhandledError)
            }
        }

        private fun observeDownloadFeedback(repository: DownloadRepository) {
            viewModelScope.launch {
                var previousStatuses: Map<String, DownloadStatus>? = null
                repository.downloads.collect { tasks ->
                    previousStatuses?.let { previous ->
                        downloadFailureFeedback(previous, tasks)?.let { feedback ->
                            appEventBus.send(
                                AppEvent.ShowSnackbar(
                                    message = feedback.message,
                                    duration = SnackbarDuration.Long,
                                ),
                            )
                        }
                    }
                    previousStatuses = tasks.associate { it.id to it.status }
                }
            }
        }

        private suspend fun handleUnhandledError(error: Throwable) {
            val message =
                when (error) {
                    is AppNetworkException -> {
                        when (error.reason) {
                            NetworkFailureReason.TIMEOUT -> "Request timed out"
                            NetworkFailureReason.CONNECTION -> "Check your internet connection"
                            NetworkFailureReason.UNKNOWN -> "Network connection failed"
                        }
                    }

                    is AppRequestException -> {
                        when (error.errorCode) {
                            AppErrorCode.UNKNOWN -> "Network request failed"
                        }
                    }

                    is AppDecodingException -> {
                        "Unable to read server response"
                    }

                    else -> {
                        "Something went wrong"
                    }
                }
            appEventBus.send(AppEvent.ShowSnackbar(UiText.Dynamic(message)))
        }
    }

private const val SETTINGS_STOP_TIMEOUT_MILLIS = 5_000L

internal data class DownloadFailureFeedback(
    val title: String?,
    val count: Int = 1,
)

internal fun downloadFailureFeedback(
    previousStatuses: Map<String, DownloadStatus>,
    tasks: List<DownloadTask>,
): DownloadFailureFeedback? {
    val failures =
        tasks.filter { task ->
            task.status == DownloadStatus.FAILED && previousStatuses[task.id] != DownloadStatus.FAILED
        }
    if (failures.isEmpty()) return null
    return DownloadFailureFeedback(
        title = failures.singleOrNull()?.title,
        count = failures.size,
    )
}

private val DownloadFailureFeedback.message: UiText
    get() =
        if (count == 1 && title != null) {
            UiText.Resource(R.string.download_feedback_failed, listOf(title))
        } else {
            UiText.Resource(R.string.download_feedback_failed_multiple, listOf(count))
        }
