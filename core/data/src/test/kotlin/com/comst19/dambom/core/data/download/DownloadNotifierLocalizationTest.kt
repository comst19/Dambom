package com.comst19.dambom.core.data.download

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.data.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], qualifiers = "ko")
class DownloadNotifierLocalizationTest {
    @Test
    fun `language change refreshes notification text and existing channel names`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notifier = DownloadNotifier(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val originalTitle =
            notifier
                .foreground()
                .notification.extras
                .getString(Notification.EXTRA_TITLE)
        val originalChannel = manager.getNotificationChannel("downloads").name.toString()

        RuntimeEnvironment.setQualifiers("hi")
        val title =
            notifier
                .foreground()
                .notification.extras
                .getString(Notification.EXTRA_TITLE)
        val channel = manager.getNotificationChannel("downloads").name.toString()

        assertNotEquals(originalTitle, title)
        assertNotEquals(originalChannel, channel)
        assertEquals(context.getString(R.string.download_notification_title), title)
        assertEquals(context.getString(R.string.download_notification_channel), channel)
    }
}
