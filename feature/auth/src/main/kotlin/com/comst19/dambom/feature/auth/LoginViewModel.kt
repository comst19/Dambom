package com.comst19.dambom.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class LoginViewModel
    @Inject
    constructor(
        private val navigation: NavigationDispatcher,
    ) : ViewModel() {
        fun login() {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.SetRoot(HomeKey))
            }
        }
    }
