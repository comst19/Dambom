package com.comst19.dambom.core.common.util

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class VideoExportGate(
    maxConcurrentExports: Int,
) {
    private val permit = Semaphore(maxConcurrentExports)

    suspend fun <T> run(export: suspend () -> T): T = permit.withPermit { export() }

    companion object {
        val shared = VideoExportGate(1)
    }
}
