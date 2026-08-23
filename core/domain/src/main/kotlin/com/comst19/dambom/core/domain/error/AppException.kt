package com.comst19.dambom.core.domain.error

sealed class AppException(
    message: String,
    cause: Throwable,
) : Exception(message, cause)

class AppNetworkException(
    val reason: NetworkFailureReason,
    cause: Throwable,
) : AppException("Network connection failed", cause)

class AppRequestException(
    val statusCode: Int,
    val errorCode: AppErrorCode,
    val rawErrorCode: String?,
    message: String?,
    cause: Throwable,
) : AppException(message ?: "Network request failed", cause)

class AppDecodingException(
    cause: Throwable,
) : AppException("Response decoding failed", cause)

enum class NetworkFailureReason {
    TIMEOUT,
    CONNECTION,
    UNKNOWN,
}

enum class AppErrorCode {
    DUPLICATE_NICKNAME,
    TOKEN_EXPIRED,
    UNKNOWN,
    ;

    companion object {
        fun from(value: String?): AppErrorCode = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
