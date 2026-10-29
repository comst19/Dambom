package com.comst19.dambom.feature.library.subtitle.work

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
import androidx.work.WorkManager
import com.comst19.dambom.feature.library.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

internal class SubtitleNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val manager = context.getSystemService(NotificationManager::class.java)

        fun foreground(
            workId: UUID,
            title: String,
            time: Long,
        ): ForegroundInfo {
            channel()
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle(context.getString(R.string.subtitle_notification_running))
                    .setContentText(
                        context.getString(R.string.subtitle_notification_progress, title, time / MILLIS_PER_SECOND),
                    ).setContentIntent(contentIntent())
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        context.getString(R.string.subtitle_cancel),
                        WorkManager.getInstance(context).createCancelPendingIntent(workId),
                    ).build()
            val type =
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                    }

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    }

                    else -> {
                        0
                    }
                }
            return ForegroundInfo(workId.hashCode(), notification, type)
        }

        fun finished(
            videoId: String,
            title: String,
            success: Boolean,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            channel()
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(
                        context.getString(
                            if (success) {
                                R.string.subtitle_notification_complete
                            } else {
                                R.string.subtitle_notification_failed
                            },
                        ),
                    ).setContentText(title)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent())
                    .build()
            manager.notify(RESULT_TAG + videoId, 0, notification)
        }

        private fun contentIntent(): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun channel() {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.subtitle_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

private const val CHANNEL = "translated-subtitles"
private const val RESULT_TAG = "subtitle-result:"
private const val MILLIS_PER_SECOND = 1_000
