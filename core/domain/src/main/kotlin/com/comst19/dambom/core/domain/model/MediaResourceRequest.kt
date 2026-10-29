package com.comst19.dambom.core.domain.model

data class MediaResourceRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val method: String = "GET",
    val thumbnailUrl: String? = null,
    val title: String? = null,
    val isProtected: Boolean = false,
)
