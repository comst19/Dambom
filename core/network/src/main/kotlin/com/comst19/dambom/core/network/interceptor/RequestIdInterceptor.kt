package com.comst19.dambom.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID
import javax.inject.Inject

class RequestIdInterceptor
    @Inject
    constructor() : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.header(REQUEST_ID_HEADER) != null) return chain.proceed(request)

            return chain.proceed(
                request
                    .newBuilder()
                    .header(REQUEST_ID_HEADER, UUID.randomUUID().toString())
                    .build(),
            )
        }
    }

private const val REQUEST_ID_HEADER = "X-Request-Id"
