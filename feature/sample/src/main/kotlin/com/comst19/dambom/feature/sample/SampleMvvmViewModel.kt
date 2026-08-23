package com.comst19.dambom.feature.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.suspendRunCatching
import com.comst19.dambom.core.common.ui.ViewModelJobLauncher
import com.comst19.dambom.core.domain.usecase.ObserveSamplesUseCase
import com.comst19.dambom.core.domain.usecase.RefreshSamplesUseCase
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.SampleDetailKey
import com.comst19.dambom.feature.sample.contract.SampleState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SampleMvvmViewModel
    @Inject
    constructor(
        observeSamples: ObserveSamplesUseCase,
        private val refreshSamples: RefreshSamplesUseCase,
        private val navigation: NavigationDispatcher,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(SampleState())
        val state: StateFlow<SampleState> = mutableState.asStateFlow()
        private val jobs = ViewModelJobLauncher<JobKey>(viewModelScope)

        init {
            viewModelScope.launch {
                observeSamples().collect { samples ->
                    mutableState.update {
                        it.copy(isLoading = false, items = samples.toUiModels())
                    }
                }
            }
            refresh()
        }

        fun refresh() {
            jobs.launchIfIdle(JobKey.Refresh) {
                mutableState.update { it.copy(isLoading = true, errorMessage = null) }
                suspendRunCatching { refreshSamples() }.fold(
                    onSuccess = {
                        mutableState.update { it.copy(isLoading = false) }
                    },
                    onFailure = {
                        mutableState.update {
                            it.copy(isLoading = false, errorMessage = "Refresh failed")
                        }
                    },
                )
            }
        }

        fun onItemClick(id: Long) {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(SampleDetailKey(id)))
            }
        }

        private enum class JobKey {
            Refresh,
        }
    }
