package com.comst19.dambom.feature.library.subtitle.model

internal enum class ModelDownloadStage { PREPARING, DOWNLOADING, VERIFYING }

internal data class ModelDownloadProgress(
    val bytes: Long = 0,
    val totalBytes: Long = 0,
    val remainingSeconds: Long? = null,
    val stage: ModelDownloadStage = ModelDownloadStage.PREPARING,
) {
    val fraction: Float?
        get() = totalBytes.takeIf { it > 0 }?.let { (bytes.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }
}

internal fun modelDownloadProgress(
    bytes: Long,
    totalBytes: Long,
    elapsedMillis: Long,
): ModelDownloadProgress {
    val remaining =
        if (bytes > 0 && elapsedMillis >= MILLIS_PER_SECOND) {
            ((totalBytes - bytes).coerceAtLeast(0).toDouble() / bytes * elapsedMillis / MILLIS_PER_SECOND).toLong()
        } else {
            null
        }
    return ModelDownloadProgress(bytes, totalBytes, remaining, ModelDownloadStage.DOWNLOADING)
}

private const val MILLIS_PER_SECOND = 1_000
