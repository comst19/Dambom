package com.comst19.dambom.core.network.header

import com.comst19.dambom.core.common.net.MediaHeaderPolicy
import javax.inject.Inject
import javax.inject.Singleton
import com.comst19.dambom.core.network.header.mediaHeadersForUrl as okHttpMediaHeadersForUrl

@Singleton
class OkHttpMediaHeaderPolicy
    @Inject
    constructor() : MediaHeaderPolicy {
        override fun mediaHeadersForUrl(
            headers: Map<String, String>,
            sourceUrl: String,
            targetUrl: String,
        ): Map<String, String> = okHttpMediaHeadersForUrl(headers, sourceUrl, targetUrl)
    }
