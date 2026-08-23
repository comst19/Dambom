package com.comst19.dambom.feature.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.SampleMatchingGraph.SampleMatchingDetailKey
import com.comst19.dambom.core.navigation.contract.SampleMatchingGraph.SampleMatchingProfileEditKey
import com.comst19.dambom.core.navigation.contract.SampleProfileGraph.SampleProfileEditKey
import com.comst19.dambom.core.navigation.contract.SampleProfileGraph.SampleProfileKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SampleNavigationDemoViewModel
    @Inject
    constructor(
        private val navigation: NavigationDispatcher,
    ) : ViewModel() {
        /** Matching root 위에 Matching Detail을 일반 Navigate합니다. */
        fun openMatchingDetail() {
            dispatch(NavigationEvent.Navigate(SampleMatchingDetailKey))
        }

        /** Profile Edit UI를 Matching stack에 쌓아 Back 시 Matching Detail로 바로 돌아갑니다. */
        fun openProfileEditReturningToMatching() {
            dispatch(NavigationEvent.Navigate(SampleMatchingProfileEditKey))
        }

        /** Profile synthetic stack을 만들고 Back 시 Profile을 거쳐 앱의 지정 Home으로 돌아갑니다. */
        fun openProfileEditThroughProfile() {
            dispatch(
                NavigationEvent.NavigateDeepLink(
                    topLevelKey = SampleProfileKey,
                    backStack = listOf(SampleProfileKey, SampleProfileEditKey),
                ),
            )
        }

        /** Profile root에서 Edit을 일반 Navigate해 Back 시 Profile root로 돌아갑니다. */
        fun openProfileEdit() {
            dispatch(NavigationEvent.Navigate(SampleProfileEditKey))
        }

        private fun dispatch(event: NavigationEvent) {
            viewModelScope.launch { navigation.dispatch(event) }
        }
    }
