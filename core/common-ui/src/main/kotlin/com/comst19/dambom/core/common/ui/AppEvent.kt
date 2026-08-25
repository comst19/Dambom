package com.comst19.dambom.core.common.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AppEvent {
    data class ShowSnackbar(
        val message: UiText,
        val duration: SnackbarDuration = SnackbarDuration.Short,
    ) : AppEvent
}

enum class SnackbarDuration {
    Short,
    Long,
    Indefinite,
}

@Singleton
class AppEventBus
    @Inject
    constructor() {
        private val channel = Channel<AppEvent>(capacity = Channel.BUFFERED)

        val events: Flow<AppEvent> = channel.receiveAsFlow()

        suspend fun send(event: AppEvent) {
            channel.send(event)
        }
    }
