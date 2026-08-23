package com.comst19.dambom.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.ui.SnackbarEvent
import com.comst19.dambom.core.common.ui.SnackbarEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.SampleMviKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.SampleMvvmKey
import com.comst19.dambom.core.navigation.contract.SettingsGraph.SettingsKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class HomeViewModel
    @Inject
    constructor(
        private val navigation: NavigationDispatcher,
        private val snackbarEventBus: SnackbarEventBus,
    ) : ViewModel() {
        fun openMvvmSample() {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(SampleMvvmKey))
            }
        }

        fun openMviSample() {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(SampleMviKey))
            }
        }

        fun openSettings() {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.NavigateTopLevel(SettingsKey))
            }
        }

        fun showSnackbar() {
            viewModelScope.launch {
                snackbarEventBus.send(
                    SnackbarEvent(message = UiText.Dynamic("Snackbar event received")),
                )
            }
        }
    }
