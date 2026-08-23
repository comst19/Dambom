package com.comst19.dambom.feature.sample

import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.suspendRunCatching
import com.comst19.dambom.core.common.ui.MviViewModel
import com.comst19.dambom.core.common.ui.SnackbarEvent
import com.comst19.dambom.core.common.ui.SnackbarEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.ui.ViewModelJobLauncher
import com.comst19.dambom.core.domain.usecase.ObserveSamplesUseCase
import com.comst19.dambom.core.domain.usecase.RefreshSamplesUseCase
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.SampleDetailKey
import com.comst19.dambom.feature.sample.contract.SampleEffect
import com.comst19.dambom.feature.sample.contract.SampleIntent
import com.comst19.dambom.feature.sample.contract.SampleState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SampleMviViewModel
    @Inject
    constructor(
        observeSamples: ObserveSamplesUseCase,
        private val refreshSamples: RefreshSamplesUseCase,
        private val snackbarEventBus: SnackbarEventBus,
        private val navigation: NavigationDispatcher,
    ) : MviViewModel<SampleState, SampleIntent, SampleEffect>(
            initialState = SampleState(),
        ) {
        private val jobs = ViewModelJobLauncher<JobKey>(viewModelScope)

        init {
            viewModelScope.launch {
                observeSamples().collect { samples ->
                    reduce { copy(isLoading = false, items = samples.toUiModels()) }
                }
            }
            onIntent(SampleIntent.Refresh)
        }

        override fun onIntent(intent: SampleIntent) {
            when (intent) {
                SampleIntent.Refresh -> {
                    refresh()
                }

                is SampleIntent.ClickItem -> {
                    viewModelScope.launch {
                        navigation.dispatch(NavigationEvent.Navigate(SampleDetailKey(intent.id)))
                    }
                }
            }
        }

        private fun refresh() {
            jobs.launchIfIdle(JobKey.Refresh) {
                reduce { copy(isLoading = true, errorMessage = null) }
                suspendRunCatching { refreshSamples() }.fold(
                    onSuccess = {
                        reduce { copy(isLoading = false) }
                    },
                    onFailure = {
                        reduce { copy(isLoading = false, errorMessage = "Refresh failed") }
                        snackbarEventBus.send(
                            SnackbarEvent(
                                message = UiText.Dynamic("Refresh failed"),
                            ),
                        )
                    },
                )
            }
        }

        private enum class JobKey {
            Refresh,
        }
    }
