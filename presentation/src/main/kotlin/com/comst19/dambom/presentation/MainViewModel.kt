package com.comst19.dambom.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.suspendRunCatching
import com.comst19.dambom.core.common.ui.SnackbarEvent
import com.comst19.dambom.core.common.ui.SnackbarEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.domain.error.AppDecodingException
import com.comst19.dambom.core.domain.error.AppErrorCode
import com.comst19.dambom.core.domain.error.AppNetworkException
import com.comst19.dambom.core.domain.error.AppRequestException
import com.comst19.dambom.core.domain.error.ErrorHandler
import com.comst19.dambom.core.domain.error.NetworkFailureReason
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        repository: SettingsRepository,
        private val startupCoordinator: StartupCoordinator,
        private val errorHandler: ErrorHandler,
        private val snackbarEventBus: SnackbarEventBus,
    ) : ViewModel() {
        private val _startupState = MutableStateFlow<AppStartupState>(AppStartupState.Initializing)
        val startupState: StateFlow<AppStartupState> = _startupState.asStateFlow()

        val settings: StateFlow<AppSettings> =
            repository.settings.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SETTINGS_STOP_TIMEOUT_MILLIS),
                initialValue = AppSettings(),
            )

        init {
            initializeStartup()
            observeUnhandledErrors()
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
                            AppErrorCode.DUPLICATE_NICKNAME -> {
                                "Nickname is already in use"
                            }

                            AppErrorCode.TOKEN_EXPIRED -> {
                                // 인증 기능을 추가하면 세션을 지우고 로그인 back stack으로 교체합니다.
                                "Authentication required"
                            }

                            AppErrorCode.UNKNOWN -> {
                                "Network request failed"
                            }
                        }
                    }

                    is AppDecodingException -> {
                        "Unable to read server response"
                    }

                    else -> {
                        "Something went wrong"
                    }
                }
            snackbarEventBus.send(SnackbarEvent(UiText.Dynamic(message)))
        }
    }

private const val SETTINGS_STOP_TIMEOUT_MILLIS = 5_000L
