package com.comst19.dambom.core.data.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.comst19.dambom.core.data.R
import com.comst19.dambom.core.domain.model.DownloadFailureReason
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DownloadNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        fun foreground(
            title: String? = null,
            downloadedBytes: Long = 0L,
            totalBytes: Long? = null,
        ): ForegroundInfo {
            ensureChannels()
            val notification =
                NotificationCompat
                    .Builder(context, DOWNLOAD_PROGRESS_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(
                        title?.let { context.getString(R.string.download_notification_progress_title, it) }
                            ?: context.getString(R.string.download_notification_title),
                    ).setContentText(progressText(downloadedBytes, totalBytes))
                    .setContentIntent(contentIntent())
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setProgress(PROGRESS_MAX, progress(downloadedBytes, totalBytes), totalBytes == null)
                    .build()
            val serviceType =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            return ForegroundInfo(DOWNLOAD_PROGRESS_NOTIFICATION_ID, notification, serviceType)
        }

        fun completed(
            taskId: String,
            title: String,
        ) {
            notifyResult(
                taskId = taskId,
                icon = android.R.drawable.stat_sys_download_done,
                title = context.getString(R.string.download_notification_completed),
                text = context.getString(R.string.download_notification_completed_description, title),
            )
        }

        fun failed(
            taskId: String,
            title: String,
            reason: DownloadFailureReason,
        ) {
            notifyResult(
                taskId = taskId,
                icon = android.R.drawable.stat_notify_error,
                title = context.getString(R.string.download_notification_failed),
                text = context.getString(reason.notificationDescription, title),
            )
        }

        private fun notifyResult(
            taskId: String,
            icon: Int,
            title: String,
            text: String,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            ensureChannels()
            val notification =
                NotificationCompat
                    .Builder(context, DOWNLOAD_RESULT_CHANNEL_ID)
                    .setSmallIcon(icon)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(contentIntent())
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .build()
            notificationManager.notify(taskId.resultNotificationId(), notification)
        }

        private fun ensureChannels() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            notificationManager.createNotificationChannels(
                listOf(
                    NotificationChannel(
                        DOWNLOAD_PROGRESS_CHANNEL_ID,
                        context.getString(R.string.download_notification_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                    NotificationChannel(
                        DOWNLOAD_RESULT_CHANNEL_ID,
                        context.getString(R.string.download_result_notification_channel),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                ),
            )
        }

        private fun contentIntent(): PendingIntent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        private fun progressText(
            downloadedBytes: Long,
            totalBytes: Long?,
        ): String =
            totalBytes
                ?.takeIf { it > 0L }
                ?.let { context.getString(R.string.download_notification_progress, progress(downloadedBytes, it)) }
                ?: context.getString(R.string.download_notification_description)
    }

private fun progress(
    downloadedBytes: Long,
    totalBytes: Long?,
): Int =
    totalBytes
        ?.takeIf { it > 0L }
        ?.let { ((downloadedBytes.coerceAtLeast(0L) * PROGRESS_MAX) / it).toInt().coerceIn(0, PROGRESS_MAX) }
        ?: 0

private fun String.resultNotificationId(): Int = RESULT_NOTIFICATION_ID_BASE + (hashCode() and RESULT_NOTIFICATION_ID_MASK)

private val DownloadFailureReason.notificationDescription: Int
    get() =
        when (this) {
            DownloadFailureReason.NETWORK -> R.string.download_notification_failed_network
            DownloadFailureReason.STORAGE -> R.string.download_notification_failed_storage
            else -> R.string.download_notification_failed_description
        }

private const val DOWNLOAD_PROGRESS_CHANNEL_ID = "downloads"
private const val DOWNLOAD_RESULT_CHANNEL_ID = "download-results"
private const val DOWNLOAD_PROGRESS_NOTIFICATION_ID = 1001
private const val RESULT_NOTIFICATION_ID_BASE = 10_000
private const val RESULT_NOTIFICATION_ID_MASK = 0x0FFF
private const val PROGRESS_MAX = 100
