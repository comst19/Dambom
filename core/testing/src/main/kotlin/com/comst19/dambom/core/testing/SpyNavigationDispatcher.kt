package com.comst19.dambom.core.testing

import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class SpyNavigationDispatcher : NavigationDispatcher {
    private val mutableEvents = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 16)
    override val events: Flow<NavigationEvent> = mutableEvents
    val dispatched = mutableListOf<NavigationEvent>()

    override suspend fun dispatch(event: NavigationEvent) {
        dispatched += event
        mutableEvents.emit(event)
    }
}
