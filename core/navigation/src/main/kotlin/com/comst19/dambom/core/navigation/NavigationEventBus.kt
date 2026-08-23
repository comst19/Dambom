package com.comst19.dambom.core.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * feature가 navigation 명령을 발행하고 앱 셸이 한 곳에서 수집하기 위한 계약입니다.
 * ViewModel에서는 [dispatch]만 호출하고 [events]는 presentation의 단일 collector만 수집합니다.
 */
interface NavigationDispatcher {
    /** 발행 순서대로 전달되는 navigation 명령 Flow입니다. 앱 셸 외부에서는 수집하지 않습니다. */
    val events: Flow<NavigationEvent>

    /**
     * navigation 명령을 queue에 추가합니다. bounded buffer가 가득 차면 공간이 생길 때까지 suspend합니다.
     * 사용자 동작이나 비즈니스 결과로 화면 이동이 확정된 ViewModel coroutine에서 호출합니다.
     */
    suspend fun dispatch(event: NavigationEvent)
}

/** [Channel]로 navigation 명령 순서와 backpressure를 보장하는 앱 전역 구현입니다. */
@Singleton
class ChannelNavigationDispatcher
    @Inject
    constructor() : NavigationDispatcher {
        private val channel = Channel<NavigationEvent>(capacity = Channel.BUFFERED)

        override val events: Flow<NavigationEvent> = channel.receiveAsFlow()

        /** [event]를 유실 없이 단일 소비자 queue로 전달합니다. */
        override suspend fun dispatch(event: NavigationEvent) {
            channel.send(event)
        }
    }
