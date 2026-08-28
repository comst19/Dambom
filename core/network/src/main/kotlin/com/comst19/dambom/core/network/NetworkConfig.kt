package com.comst19.dambom.core.network

import com.comst19.dambom.core.common.model.AppEnvironment
import okhttp3.logging.HttpLoggingInterceptor

data class NetworkConfig(
    val logLevel: HttpLoggingInterceptor.Level,
) {
    companion object {
        fun from(appEnvironment: AppEnvironment): NetworkConfig =
            NetworkConfig(
                logLevel =
                    when (appEnvironment) {
                        AppEnvironment.DEBUG,
                        AppEnvironment.QA,
                        -> HttpLoggingInterceptor.Level.BASIC

                        AppEnvironment.RELEASE -> HttpLoggingInterceptor.Level.NONE
                    },
            )
    }
}
