package com.comst19.dambom.core.domain.error

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorHandler
    @Inject
    constructor() {
        private val channel = Channel<Throwable>(capacity = Channel.BUFFERED)

        val errors: Flow<Throwable> = channel.receiveAsFlow()

        suspend fun handle(error: Throwable) {
            channel.send(error)
        }
    }
