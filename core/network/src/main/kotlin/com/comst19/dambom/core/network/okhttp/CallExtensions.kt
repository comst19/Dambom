package com.comst19.dambom.core.network.okhttp

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

suspend fun <T> Call.executeCancellable(transform: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    if (!continuation.isActive) {
                        response.close()
                        return
                    }
                    continuation.resumeWith(runCatching { response.use(transform) })
                }
            },
        )
    }
