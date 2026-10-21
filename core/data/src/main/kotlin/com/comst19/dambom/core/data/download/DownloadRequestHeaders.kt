package com.comst19.dambom.core.data.download

import com.comst19.dambom.core.network.header.decodeMediaHeaders

internal fun decodeDownloadHeaders(value: String): Map<String, String> = decodeMediaHeaders(value)
