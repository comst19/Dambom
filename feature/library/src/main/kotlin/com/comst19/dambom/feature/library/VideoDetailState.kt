package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal sealed interface VideoDetailState {
    data object Loading : VideoDetailState

    data object NotFound : VideoDetailState

    data object Error : VideoDetailState

    data class Ready(
        val task: DownloadTask,
    ) : VideoDetailState
}

internal fun Flow<DownloadTask?>.asVideoDetailState(): Flow<VideoDetailState> =
    map<DownloadTask?, VideoDetailState> { task ->
        if (task?.status == DownloadStatus.COMPLETED && task.localFilePath != null) {
            VideoDetailState.Ready(task)
        } else {
            VideoDetailState.NotFound
        }
    }.onStart { emit(VideoDetailState.Loading) }
        .catch { failure ->
            if (failure is CancellationException) throw failure
            emit(VideoDetailState.Error)
        }
