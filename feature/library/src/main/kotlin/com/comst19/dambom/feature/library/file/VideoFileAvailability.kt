package com.comst19.dambom.feature.library.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val availability =
        remember(task?.id, task?.status, task?.localFilePath) {
            mutableStateOf(isLocalVideoAvailable(task))
        }
    val latestTask by rememberUpdatedState(task)
    LaunchedEffect(task?.id, task?.status, task?.localFilePath) {
        val path = task?.localFilePath ?: return@LaunchedEffect
        observeLocalVideoChanges(path).collect {
            availability.value = isLocalVideoAvailable(latestTask)
        }
    }
    return availability
}

internal fun isTargetFileEvent(
    path: String?,
    targetName: String,
): Boolean = path == targetName
