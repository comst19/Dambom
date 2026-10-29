package com.comst19.dambom.feature.library.subtitle.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.feature.library.subtitle.data.SubtitleFiles
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleEngine
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair
import com.comst19.dambom.feature.library.subtitle.model.subtitleFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltWorker
internal class SubtitleWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted parameters: WorkerParameters,
        private val repository: DownloadRepository,
        private val engine: SubtitleEngine,
        private val files: SubtitleFiles,
        private val notifier: SubtitleNotifier,
    ) : CoroutineWorker(context, parameters) {
        @Suppress("ReturnCount", "TooGenericExceptionCaught")
        override suspend fun doWork(): Result {
            val videoId = inputData.getString(SUBTITLE_VIDEO_ID) ?: return Result.failure()
            val pair =
                SubtitleLanguagePair.entries.firstOrNull { it.name == inputData.getString(SUBTITLE_PAIR) }
                    ?: return Result.failure()
            var title = ""
            return try {
                val task = checkNotNull(repository.observeDownload(videoId).first())
                check(task.status == DownloadStatus.COMPLETED && !task.deletePending)
                title = task.title
                val path = checkNotNull(task.localFilePath)
                setForeground(notifier.foreground(id, title, 0))
                coroutineScope {
                    val progress = Channel<Long>(Channel.CONFLATED)
                    val updates =
                        launch {
                            for (time in progress) {
                                setProgress(workDataOf(SUBTITLE_PROGRESS to time))
                                setForeground(notifier.foreground(id, title, time))
                            }
                        }
                    try {
                        val cues = engine.generate(path, pair) { progress.trySend(it) }
                        check(cues.isNotEmpty())
                        val current = repository.observeDownload(videoId).first()
                        check(current != null && !current.deletePending && current.localFilePath == path)
                        files.save(path, cues)
                    } finally {
                        progress.close()
                        updates.join()
                    }
                }
                notifier.finished(videoId, title, true)
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                notifier.finished(videoId, title, false)
                Result.failure(workDataOf(SUBTITLE_ERROR to subtitleFailure(failure).name))
            }
        }
    }
