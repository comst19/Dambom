package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.data.remote.error.RemoteDataException
import com.comst19.dambom.core.data.remote.error.RemoteDecodingException
import com.comst19.dambom.core.data.remote.error.RemoteNetworkException
import com.comst19.dambom.core.data.remote.error.RemoteNetworkFailureReason
import com.comst19.dambom.core.data.remote.error.RemoteRequestException
import com.comst19.dambom.core.domain.error.AppDecodingException
import com.comst19.dambom.core.domain.error.AppErrorCode
import com.comst19.dambom.core.domain.error.AppException
import com.comst19.dambom.core.domain.error.AppNetworkException
import com.comst19.dambom.core.domain.error.AppRequestException
import com.comst19.dambom.core.domain.error.NetworkFailureReason

internal suspend inline fun <T> withRemoteErrorMapping(block: () -> T): T =
    try {
        block()
    } catch (exception: RemoteDataException) {
        throw exception.toDomain()
    }

private fun RemoteDataException.toDomain(): AppException =
    when (this) {
        is RemoteRequestException -> {
            AppRequestException(
                statusCode = statusCode,
                errorCode = AppErrorCode.from(errorCode),
                rawErrorCode = errorCode,
                message = message,
                cause = this,
            )
        }

        is RemoteNetworkException -> {
            AppNetworkException(
                reason = reason.toDomain(),
                cause = this,
            )
        }

        is RemoteDecodingException -> {
            AppDecodingException(this)
        }
    }

private fun RemoteNetworkFailureReason.toDomain(): NetworkFailureReason =
    when (this) {
        RemoteNetworkFailureReason.TIMEOUT -> NetworkFailureReason.TIMEOUT
        RemoteNetworkFailureReason.CONNECTION -> NetworkFailureReason.CONNECTION
        RemoteNetworkFailureReason.UNKNOWN -> NetworkFailureReason.UNKNOWN
    }
