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
                        downloadFeedback(previous, tasks).forEach { feedback ->
                            appEventBus.send(
                                AppEvent.ShowSnackbar(
                                    message = UiText.Resource(feedback.type.messageRes, listOf(feedback.title)),
                                    duration =
                                        if (feedback.type == DownloadFeedbackType.FAILED) {
                                            SnackbarDuration.Long
                                        } else {
                                            SnackbarDuration.Short
                                        },
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

internal data class DownloadFeedback(
    val type: DownloadFeedbackType,
    val title: String,
)

internal enum class DownloadFeedbackType {
    QUEUED,
    STARTED,
    COMPLETED,
    FAILED,
}

internal fun downloadFeedback(
    previousStatuses: Map<String, DownloadStatus>,
    tasks: List<DownloadTask>,
): List<DownloadFeedback> =
    tasks.mapNotNull { task ->
        val previous = previousStatuses[task.id]
        val type =
            when (task.status) {
                DownloadStatus.QUEUED -> {
                    if (previous == null || previous == DownloadStatus.PAUSED || previous == DownloadStatus.FAILED) {
                        DownloadFeedbackType.QUEUED
                    } else {
                        null
                    }
                }

                DownloadStatus.DOWNLOADING -> {
                    DownloadFeedbackType.STARTED.takeIf { previous != DownloadStatus.DOWNLOADING }
                }

                DownloadStatus.COMPLETED -> {
                    DownloadFeedbackType.COMPLETED.takeIf { previous != DownloadStatus.COMPLETED }
                }

                DownloadStatus.FAILED -> {
                    DownloadFeedbackType.FAILED.takeIf { previous != DownloadStatus.FAILED }
                }

                DownloadStatus.PAUSED -> {
                    null
                }
            }
        type?.let { DownloadFeedback(it, task.title) }
    }

private val DownloadFeedbackType.messageRes: Int
    get() =
        when (this) {
            DownloadFeedbackType.QUEUED -> R.string.download_feedback_queued
            DownloadFeedbackType.STARTED -> R.string.download_feedback_started
            DownloadFeedbackType.COMPLETED -> R.string.download_feedback_completed
            DownloadFeedbackType.FAILED -> R.string.download_feedback_failed
        }
