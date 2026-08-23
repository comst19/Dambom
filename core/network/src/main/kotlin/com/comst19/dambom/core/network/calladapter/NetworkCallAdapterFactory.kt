package com.comst19.dambom.core.network.calladapter

import com.comst19.dambom.core.network.model.ApiErrorResponse
import com.comst19.dambom.core.network.model.ApiResponse
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkCallAdapterFactory
    @Inject
    constructor(
        private val json: Json,
    ) : CallAdapter.Factory() {
        override fun get(
            returnType: Type,
            annotations: Array<Annotation>,
            retrofit: Retrofit,
        ): CallAdapter<*, *>? {
            if (getRawType(returnType) != Call::class.java) return null
            require(returnType is ParameterizedType) { "Call return type must be parameterized" }

            val responseType = getParameterUpperBound(0, returnType)
            if (getRawType(responseType) != ApiResponse::class.java) return null

            return NetworkCallAdapter(responseType, json)
        }
    }

private class NetworkCallAdapter(
    private val responseType: Type,
    private val json: Json,
) : CallAdapter<Any, Call<Any>> {
    override fun responseType(): Type = responseType

    override fun adapt(call: Call<Any>): Call<Any> = NetworkCall(call, json)
}

private class NetworkCall<T : Any>(
    private val delegate: Call<T>,
    private val json: Json,
) : Call<T> {
    override fun enqueue(callback: Callback<T>) {
        delegate.enqueue(
            object : Callback<T> {
                override fun onResponse(
                    call: Call<T>,
                    response: Response<T>,
                ) {
                    if (response.isSuccessful) {
                        callback.onResponse(this@NetworkCall, response)
                        return
                    }

                    val error = response.errorBody()?.string()?.toApiErrorResponse()
                    callback.onFailure(
                        this@NetworkCall,
                        NetworkHttpException(
                            statusCode = response.code(),
                            errorCode = error?.code,
                            message = error?.message,
                        ),
                    )
                }

                override fun onFailure(
                    call: Call<T>,
                    throwable: Throwable,
                ) {
                    callback.onFailure(this@NetworkCall, throwable)
                }
            },
        )
    }

    override fun clone(): Call<T> = NetworkCall(delegate.clone(), json)

    override fun execute(): Response<T> = delegate.execute()

    override fun isExecuted(): Boolean = delegate.isExecuted

    override fun cancel() = delegate.cancel()

    override fun isCanceled(): Boolean = delegate.isCanceled

    override fun request(): Request = delegate.request()

    override fun timeout(): Timeout = delegate.timeout()

    private fun String.toApiErrorResponse(): ApiErrorResponse? =
        try {
            json.decodeFromString<ApiErrorResponse>(this)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
