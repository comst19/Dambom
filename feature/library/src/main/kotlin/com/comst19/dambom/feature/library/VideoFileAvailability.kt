package com.comst19.dambom.feature.library

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.comst19.dambom.core.domain.model.DownloadTask
import java.io.File

internal fun isLocalVideoAvailable(task: DownloadTask?): Boolean =
    task?.status == com.comst19.dambom.core.domain.model.DownloadStatus.COMPLETED &&
        task.localFilePath?.let(::File)?.isFile == true

@Composable
internal fun rememberLocalVideoAvailable(task: DownloadTask?): MutableState<Boolean> {
    val availability = remember(task?.id, task?.status, task?.localFilePath) { mutableStateOf(isLocalVideoAvailable(task)) }
    val latestTask by rememberUpdatedState(task)
    DisposableEffect(task?.id, task?.status, task?.localFilePath) {
        val file = task?.localFilePath?.let(::File)
        val parent = file?.parentFile
        if (file == null || parent == null) return@DisposableEffect onDispose {}
        val mainHandler = Handler(Looper.getMainLooper())
        val observer =
            object : FileObserver(parent.path, DELETE or MOVED_FROM or CREATE or MOVED_TO or CLOSE_WRITE or ATTRIB) {
                override fun onEvent(
                    event: Int,
                    path: String?,
                ) {
                    if (!isTargetFileEvent(path, file.name)) return
                    mainHandler.post { availability.value = isLocalVideoAvailable(latestTask) }
                }
            }
        observer.startWatching()
        onDispose { observer.stopWatching() }
    }
    return availability
}

internal fun isTargetFileEvent(
    path: String?,
    targetName: String,
): Boolean = path == targetName
