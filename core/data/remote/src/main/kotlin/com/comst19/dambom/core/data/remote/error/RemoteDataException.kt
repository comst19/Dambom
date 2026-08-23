package com.comst19.dambom.core.data.remote.error

sealed class RemoteDataException(
    message: String,
    cause: Throwable,
) : Exception(message, cause)

class RemoteDecodingException(
    cause: Throwable,
) : RemoteDataException("Response decoding failed", cause)

class RemoteNetworkException(
    val reason: RemoteNetworkFailureReason,
    cause: Throwable,
) : RemoteDataException("Network connection failed", cause)

class RemoteRequestException(
    val statusCode: Int,
    val errorCode: String?,
    message: String?,
    cause: Throwable,
) : RemoteDataException(message ?: "Network request failed", cause)

enum class RemoteNetworkFailureReason {
    TIMEOUT,
    CONNECTION,
    UNKNOWN,
}
