package com.comst19.dambom.core.common.url

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedUrlBus
    @Inject
    constructor() {
        private val mutablePendingUrl = MutableStateFlow<String?>(null)
        val pendingUrl: StateFlow<String?> = mutablePendingUrl.asStateFlow()

        fun offer(text: String?) {
            mutablePendingUrl.value = text?.let(::extractHttpUrl)
        }

        fun clear() {
            mutablePendingUrl.value = null
        }
    }

internal fun extractHttpUrl(text: String): String? = HTTP_URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', ']', '}')

private val HTTP_URL_REGEX = Regex("https?://[^\\s<]+", RegexOption.IGNORE_CASE)
