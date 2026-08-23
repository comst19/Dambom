package com.comst19.dambom.core.analytics

interface AnalyticsHelper {
    fun log(event: AnalyticsEvent)
}

data class AnalyticsEvent(
    val name: String,
    val properties: Map<String, String> = emptyMap(),
)

class NoOpAnalyticsHelper
    @javax.inject.Inject
    constructor() : AnalyticsHelper {
        override fun log(event: AnalyticsEvent) = Unit
    }
