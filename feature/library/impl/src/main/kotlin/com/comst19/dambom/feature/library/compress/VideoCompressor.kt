package com.comst19.dambom.feature.library.compress

internal interface VideoCompressor {
    suspend fun inspect(path: String): CompressionSource

    suspend fun compress(
        path: String,
        profile: CompressionProfile,
        destination: String,
        onProgress: (Int?) -> Unit,
    ): Long

    suspend fun discard(destination: String)
}

internal class CompressionNotSmallerException(
    message: String? = null,
) : Exception(message)
