package com.comst19.dambom.core.common.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

abstract class MviViewModel<STATE : UiState, INTENT : UiIntent, EFFECT : UiEffect>(
    initialState: STATE,
) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<STATE> = mutableState.asStateFlow()

    private val effects = Channel<EFFECT>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    protected val currentState: STATE
        get() = mutableState.value

    protected fun reduce(reducer: STATE.() -> STATE) {
        mutableState.update(reducer)
    }

    protected suspend fun sendEffect(effect: EFFECT) {
        effects.send(effect)
    }

    abstract fun onIntent(intent: INTENT)
}
