package com.comst19.dambom.feature.library.trim.export

import com.comst19.dambom.feature.library.trim.contract.TrimSelection

internal interface ClipExporter {
    suspend fun durationMillis(path: String): Long

    suspend fun export(
        path: String,
        selection: TrimSelection,
        destination: String,
        onProgress: (Int?) -> Unit,
    )

    suspend fun discard(destination: String)
}
