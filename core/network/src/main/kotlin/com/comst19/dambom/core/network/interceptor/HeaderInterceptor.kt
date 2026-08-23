package com.comst19.dambom.core.network.interceptor

import com.comst19.dambom.core.network.header.NetworkHeaderProvider
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class HeaderInterceptor
    @Inject
    constructor(
        private val headerProvider: NetworkHeaderProvider,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val requestBuilder =
                chain
                    .request()
                    .newBuilder()
                    .header(ACCEPT_HEADER, JSON_MEDIA_TYPE)

            headerProvider.headers().forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            return chain.proceed(requestBuilder.build())
        }
    }

private const val ACCEPT_HEADER = "Accept"
private const val JSON_MEDIA_TYPE = "application/json"
