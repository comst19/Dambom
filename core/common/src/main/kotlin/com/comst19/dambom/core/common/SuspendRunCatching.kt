package com.comst19.dambom.core.common

import kotlinx.coroutines.CancellationException

@Suppress("TooGenericExceptionCaught")
suspend inline fun <T> suspendRunCatching(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (exception: CancellationException) {
        throw exception
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }
