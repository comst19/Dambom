package com.comst19.dambom.feature.library.subtitle

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.subtitle.work.SubtitleNotifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en")
class SubtitleNotifierLocalizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localeFile = "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file"

    @After fun cleanup() {
        context.deleteFile(localeFile)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test fun `notifications use the persisted app language even when application resources stay English`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        val component = ComponentName(context.packageName, "TestLauncher")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(component)
            addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
        context.openFileOutput(localeFile, Context.MODE_PRIVATE).use {
            it.write("<locales application_locales=\"fr\" />".toByteArray())
        }
        val localized = ContextCompat.getContextForLanguage(context)
        val expected = localized.getString(R.string.subtitle_notification_running)
        assertNotEquals(context.getString(R.string.subtitle_notification_running), expected)
        val notification = SubtitleNotifier(context).foreground(UUID.randomUUID(), "Video", 0).notification
        assertEquals(expected, notification.extras.getString(Notification.EXTRA_TITLE))
        val channel =
            context
                .getSystemService(NotificationManager::class.java)
                .getNotificationChannel("translated-subtitles")
        assertEquals(localized.getString(R.string.subtitle_notification_channel), channel.name.toString())
    }
}
