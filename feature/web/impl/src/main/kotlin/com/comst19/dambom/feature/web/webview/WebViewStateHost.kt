package com.comst19.dambom.feature.web.webview

import android.os.Bundle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class WebViewStateHost
    @Inject
    constructor() : ViewModel() {
        private val savedWebStates = LinkedHashMap<Long, Bundle>()

        fun saveWebState(
            tabId: Long,
            state: Bundle,
        ) {
            savedWebStates.remove(tabId)
            savedWebStates[tabId] = state
            while (savedWebStates.size > MAX_RETAINED_WEB_STATES) {
                savedWebStates.remove(savedWebStates.keys.first())
            }
        }

        fun savedWebState(tabId: Long): Bundle? = savedWebStates[tabId]

        fun removeWebState(tabId: Long) {
            savedWebStates.remove(tabId)
        }

        override fun onCleared() {
            savedWebStates.clear()
        }
    }

private const val MAX_RETAINED_WEB_STATES = 3
