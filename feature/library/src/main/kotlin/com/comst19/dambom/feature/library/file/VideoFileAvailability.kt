package com.comst19.dambom.feature.library.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.comst19.dambom.core.domain.model.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun isLocalVideoAvailable(task: DownloadTask?): Boolean =
    withContext(Dispatchers.IO) {
        task?.status == com.comst19.dambom.core.domain.model.DownloadStatus.COMPLETED &&
            task.localFilePath?.let(::File)?.isFile == true
    }

@Composable
internal fun rememberLocalVideoAvailable(
    task: DownloadTask?,
    refreshKey: Int = 0,
): MutableState<Boolean?> {
    val availability =
        remember(task?.id, task?.status, task?.localFilePath) {
            mutableStateOf<Boolean?>(null)
        }
    LaunchedEffect(task?.id, task?.status, task?.localFilePath, refreshKey) {
        val path = task?.localFilePath
        if (path == null) {
            availability.value = false
            return@LaunchedEffect
        }
        observeLocalVideoChanges(path).collect {
            availability.value = isLocalVideoAvailable(task)
        }
    }
    return availability
}

internal fun isTargetFileEvent(
    path: String?,
    targetName: String,
): Boolean = path == targetName
