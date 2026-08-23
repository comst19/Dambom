package com.comst19.dambom.core.common.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.analytics.AnalyticsHelper
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import kotlinx.coroutines.launch

abstract class BaseViewModel(
    protected val navigationDispatcher: NavigationDispatcher,
    protected val analyticsHelper: AnalyticsHelper,
) : ViewModel() {
    protected fun navigate(event: NavigationEvent) {
        viewModelScope.launch {
            navigationDispatcher.dispatch(event)
        }
    }
}
