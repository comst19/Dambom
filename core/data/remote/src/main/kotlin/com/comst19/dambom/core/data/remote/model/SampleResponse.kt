package com.comst19.dambom.core.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class SampleResponse(
    val id: Long,
    val title: String,
    val description: String? = null,
)
