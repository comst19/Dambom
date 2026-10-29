package com.comst19.dambom.feature.library.subtitle.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.feature.library.subtitle.model.SubtitleFailure
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class SubtitleJob(
    val active: Boolean,
    val completed: Boolean,
    val failed: Boolean,
    val pair: SubtitleLanguagePair,
    val processedMillis: Long = 0,
    val failure: SubtitleFailure? = null,
)

internal interface SubtitleJobs {
    fun observeModelDownload(): Flow<SubtitleModelDownload?>

    suspend fun enqueueModelDownload(pair: SubtitleLanguagePair)

    suspend fun cancelModelDownload()

    fun observe(videoId: String): Flow<SubtitleJob?>

    suspend fun enqueue(
        videoId: String,
        pair: SubtitleLanguagePair,
    )

    suspend fun cancel(videoId: String)
}

internal class WorkManagerSubtitleJobs
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : SubtitleJobs {
        private val manager = WorkManager.getInstance(context)

        override fun observeModelDownload(): Flow<SubtitleModelDownload?> =
            manager.getWorkInfosForUniqueWorkFlow(MODEL_WORK_NAME).map { infos ->
                infos
                    .maxByOrNull { info ->
                        info.tags
                            .firstOrNull { it.startsWith(CREATED_TAG) }
                            ?.removePrefix(CREATED_TAG)
                            ?.toLongOrNull() ?: 0
                    }?.let { info ->
                        SubtitleModelDownload(
                            active = !info.state.isFinished,
                            completed = info.state == WorkInfo.State.SUCCEEDED,
                            failed = info.state == WorkInfo.State.FAILED,
                            progress = info.progress.modelDownloadProgress(),
                        )
                    }
            }

        override suspend fun enqueueModelDownload(pair: SubtitleLanguagePair) =
            withContext(io) {
                val request =
                    OneTimeWorkRequestBuilder<SubtitleModelDownloadWorker>()
                        .setInputData(workDataOf(SUBTITLE_PAIR to pair.name))
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .addTag(CREATED_TAG + System.currentTimeMillis())
                        .build()
                manager.enqueueUniqueWork(MODEL_WORK_NAME, ExistingWorkPolicy.KEEP, request).result.get()
                Unit
            }

        override suspend fun cancelModelDownload() =
            withContext(io) {
                manager.cancelUniqueWork(MODEL_WORK_NAME).result.get()
                Unit
            }

        override fun observe(videoId: String): Flow<SubtitleJob?> =
            manager
                .getWorkInfosForUniqueWorkFlow(subtitleWorkName(videoId))
                .map { infos ->
                    infos
                        .maxByOrNull { info ->
                            info.tags
                                .firstOrNull { it.startsWith(CREATED_TAG) }
                                ?.removePrefix(CREATED_TAG)
                                ?.toLongOrNull() ?: 0
                        }?.let { info ->
                            val pair =
                                SubtitleLanguagePair.entries.firstOrNull { PAIR_TAG + it.name in info.tags }
                                    ?: return@map null
                            SubtitleJob(
                                active = !info.state.isFinished,
                                completed = info.state == WorkInfo.State.SUCCEEDED,
                                failed = info.state == WorkInfo.State.FAILED,
                                pair = pair,
                                processedMillis = info.progress.getLong(SUBTITLE_PROGRESS, 0),
                                failure =
                                    SubtitleFailure.entries.firstOrNull {
                                        it.name == info.outputData.getString(SUBTITLE_ERROR)
                                    },
                            )
                        }
                }

        override suspend fun enqueue(
            videoId: String,
            pair: SubtitleLanguagePair,
        ) = withContext(io) {
            val request =
                OneTimeWorkRequestBuilder<SubtitleWorker>()
                    .setInputData(workDataOf(SUBTITLE_VIDEO_ID to videoId, SUBTITLE_PAIR to pair.name))
                    .addTag(CREATED_TAG + System.currentTimeMillis())
                    .addTag(PAIR_TAG + pair.name)
                    .build()
            manager.enqueueUniqueWork(subtitleWorkName(videoId), ExistingWorkPolicy.KEEP, request).result.get()
            Unit
        }

        override suspend fun cancel(videoId: String) =
            withContext(io) {
                manager.cancelUniqueWork(subtitleWorkName(videoId)).result.get()
                Unit
            }
    }

internal fun subtitleWorkName(id: String) = "translated-subtitles:$id"

private const val CREATED_TAG = "subtitle-created:"
private const val PAIR_TAG = "subtitle-pair:"
internal const val SUBTITLE_VIDEO_ID = "subtitle-video-id"
internal const val SUBTITLE_PAIR = "subtitle-language-pair"
internal const val SUBTITLE_PROGRESS = "subtitle-processed-millis"

internal const val SUBTITLE_ERROR = "subtitle-error"
