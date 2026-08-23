package com.comst19.dambom.core.common.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SnackbarEvent(
    val message: UiText,
    val duration: SnackbarDuration = SnackbarDuration.Short,
)

enum class SnackbarDuration {
    Short,
    Long,
    Indefinite,
}

@Singleton
class SnackbarEventBus
    @Inject
    constructor() {
        private val channel = Channel<SnackbarEvent>(capacity = Channel.BUFFERED)

        val events: Flow<SnackbarEvent> = channel.receiveAsFlow()

        suspend fun send(event: SnackbarEvent) {
            channel.send(event)
        }
    }
