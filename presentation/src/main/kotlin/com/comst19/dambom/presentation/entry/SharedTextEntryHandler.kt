package com.comst19.dambom.presentation.entry

import android.content.Intent
import com.comst19.dambom.core.common.url.SharedUrlBus
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import javax.inject.Inject

internal class SharedTextEntryHandler
    @Inject
    constructor(
        private val navigationDispatcher: NavigationDispatcher,
        private val sharedUrlBus: SharedUrlBus,
    ) {
        suspend fun handle(intent: Intent) {
            if (intent.action != Intent.ACTION_SEND || intent.type != TEXT_MIME_TYPE) return
            navigationDispatcher.dispatch(NavigationEvent.NavigateTopLevel(HomeKey))
            sharedUrlBus.offer(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

private const val TEXT_MIME_TYPE = "text/plain"
