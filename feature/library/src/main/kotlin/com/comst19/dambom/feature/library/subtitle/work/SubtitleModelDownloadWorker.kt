package com.comst19.dambom.feature.library.subtitle.work

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleEngine
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleModelStatus
import com.comst19.dambom.feature.library.subtitle.model.ModelDownloadProgress
import com.comst19.dambom.feature.library.subtitle.model.ModelDownloadStage
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal data class SubtitleModelDownload(
    val active: Boolean,
    val completed: Boolean,
    val failed: Boolean,
    val progress: ModelDownloadProgress = ModelDownloadProgress(),
)

@HiltWorker
internal class SubtitleModelDownloadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted parameters: WorkerParameters,
        private val engine: SubtitleEngine,
        private val notifier: SubtitleNotifier,
    ) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result {
            val pair =
                SubtitleLanguagePair.entries.firstOrNull { it.name == inputData.getString(SUBTITLE_PAIR) }
                    ?: return Result.failure()
            return try {
                setProgress(ModelDownloadProgress().toWorkData())
                setForeground(notifier.modelForeground(id, ModelDownloadProgress()))
                val result =
                    coroutineScope {
                        val progress = Channel<ModelDownloadProgress>(Channel.CONFLATED)
                        val updates =
                            launch {
                                for (value in progress) {
                                    setProgress(value.toWorkData())
                                    setForeground(notifier.modelForeground(id, value))
                                }
                            }
                        try {
                            var lastUpdate = 0L
                            var lastStage: ModelDownloadStage? = null
                            engine.download(pair) { value ->
                                val now = SystemClock.elapsedRealtime()
                                val complete = value.totalBytes > 0 && value.bytes == value.totalBytes
                                if (value.stage != lastStage || now - lastUpdate >= MODEL_UPDATE_MILLIS || complete) {
                                    progress.trySend(value)
                                    lastUpdate = now
                                    lastStage = value.stage
                                }
                            }
                        } finally {
                            progress.close()
                            updates.join()
                        }
                    }
                if (result == SubtitleModelStatus.AVAILABLE) {
                    notifier.modelFinished(true)
                    Result.success()
                } else if (result == SubtitleModelStatus.DOWNLOADING && runAttemptCount < MAX_MODEL_RETRIES) {
                    Result.retry()
                } else {
                    notifier.modelFinished(false)
                    Result.failure()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notifier.modelFinished(false)
                Result.failure()
            }
        }
    }

internal fun ModelDownloadProgress.toWorkData(): Data =
    workDataOf(
        MODEL_BYTES to bytes,
        MODEL_TOTAL to totalBytes,
        MODEL_REMAINING to (remainingSeconds ?: -1L),
        MODEL_STAGE to stage.name,
    )

internal fun Data.modelDownloadProgress() =
    ModelDownloadProgress(
        bytes = getLong(MODEL_BYTES, 0),
        totalBytes = getLong(MODEL_TOTAL, 0),
        remainingSeconds = getLong(MODEL_REMAINING, -1).takeIf { it >= 0 },
        stage =
            ModelDownloadStage.entries.firstOrNull { it.name == getString(MODEL_STAGE) }
                ?: ModelDownloadStage.PREPARING,
    )

internal const val MODEL_WORK_NAME = "subtitle-model-download"
private const val MODEL_BYTES = "model-bytes"
private const val MODEL_TOTAL = "model-total-bytes"
private const val MODEL_REMAINING = "model-remaining-seconds"
private const val MODEL_STAGE = "model-download-stage"
private const val MAX_MODEL_RETRIES = 3

private const val MODEL_UPDATE_MILLIS = 1_000L
