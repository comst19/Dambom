package com.comst19.dambom.feature.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.domain.usecase.ObserveSampleUseCase
import com.comst19.dambom.core.navigation.contract.HomeGraph.SampleDetailKey
import com.comst19.dambom.feature.sample.contract.SampleDetailState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel(assistedFactory = SampleDetailViewModel.Factory::class)
internal class SampleDetailViewModel
    @AssistedInject
    constructor(
        observeSample: ObserveSampleUseCase,
        @Assisted key: SampleDetailKey,
    ) : ViewModel() {
        val state: StateFlow<SampleDetailState> =
            observeSample(key.id)
                .map { sample ->
                    SampleDetailState(
                        id = key.id,
                        title = sample?.title,
                        description = sample?.description,
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = SampleDetailState(id = key.id),
                )

        @AssistedFactory
        interface Factory {
            fun create(key: SampleDetailKey): SampleDetailViewModel
        }
    }
