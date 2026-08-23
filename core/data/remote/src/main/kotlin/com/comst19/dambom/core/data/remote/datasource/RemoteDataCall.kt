package com.comst19.dambom.core.data.remote.datasource

import com.comst19.dambom.core.data.remote.error.RemoteDecodingException
import com.comst19.dambom.core.data.remote.error.RemoteNetworkException
import com.comst19.dambom.core.data.remote.error.RemoteNetworkFailureReason
import com.comst19.dambom.core.data.remote.error.RemoteRequestException
import com.comst19.dambom.core.network.calladapter.NetworkHttpException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal suspend inline fun <T> remoteDataCall(crossinline block: suspend () -> T): T =
    try {
        block()
    } catch (exception: SerializationException) {
        throw RemoteDecodingException(exception)
    } catch (exception: NetworkHttpException) {
        throw RemoteRequestException(
            statusCode = exception.statusCode,
            errorCode = exception.errorCode,
            message = exception.message,
            cause = exception,
        )
    } catch (exception: HttpException) {
        throw RemoteRequestException(
            statusCode = exception.code(),
            errorCode = null,
            message = exception.message(),
            cause = exception,
        )
    } catch (exception: IOException) {
        throw RemoteNetworkException(exception.toRemoteNetworkFailureReason(), exception)
    }

private fun IOException.toRemoteNetworkFailureReason(): RemoteNetworkFailureReason =
    when (this) {
        is SocketTimeoutException -> RemoteNetworkFailureReason.TIMEOUT

        is UnknownHostException,
        is ConnectException,
        is NoRouteToHostException,
        -> RemoteNetworkFailureReason.CONNECTION

        else -> RemoteNetworkFailureReason.UNKNOWN
    }
