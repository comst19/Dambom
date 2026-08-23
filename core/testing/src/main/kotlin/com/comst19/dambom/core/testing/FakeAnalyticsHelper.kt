package com.comst19.dambom.core.testing

import com.comst19.dambom.core.analytics.AnalyticsEvent
import com.comst19.dambom.core.analytics.AnalyticsHelper

class FakeAnalyticsHelper : AnalyticsHelper {
    val events = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }
}
