package com.comst19.dambom.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val data: T,
    val message: String? = null,
    val code: String? = null,
)

@Serializable
data class ApiErrorResponse(
    val message: String? = null,
    val code: String? = null,
)

fun <T> ApiResponse<T>.unwrapData(): T = data
