package com.comst19.dambom.core.domain.model

data class MediaResourceRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val method: String = "GET",
)
