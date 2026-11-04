package com.comst19.dambom.core.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrivateVideoSession {
    private val unlocked = MutableStateFlow(false)
    val isUnlocked = unlocked.asStateFlow()

    fun unlock() {
        unlocked.value = true
    }

    fun lock() {
        unlocked.value = false
    }
}
