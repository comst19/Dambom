package com.comst19.dambom.feature.library

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSpeedPersistenceTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun `recovered preference applies without rewriting it and later user choice wins`() =
        runTest {
            val settings = SpeedSettings()
            val viewModel =
                VideoPlayerViewModel(
                    ApplicationProvider.getApplicationContext<Context>(),
                    LibraryTestPlaybackHistory(),
                    settings,
                    backgroundScope,
                    AppEventBus(),
                )
            val scopeJob = checkNotNull(viewModel.viewModelScope.coroutineContext[Job])
            try {
                runCurrent()
                settings.settings.value = AppSettings(playbackSpeed = 1.5f)
                runCurrent()
                assertEquals(1.5f, viewModel.player.playbackParameters.speed)
                assertEquals(emptyList<Float>(), settings.saved)
                viewModel.player.setPlaybackSpeed(2f)
                settings.settings.value = AppSettings(playbackSpeed = 1.25f)
                runCurrent()
                assertEquals(2f, viewModel.player.playbackParameters.speed)
                assertEquals(listOf(2f), settings.saved)
            } finally {
                viewModel.closeForTest()
                assertTrue(scopeJob.isCancelled)
            }
        }
}

private class SpeedSettings : SettingsRepository by LibraryTestSettings {
    override val settings = MutableStateFlow(AppSettings())
    val saved = mutableListOf<Float>()

    override suspend fun setPlaybackSpeed(speed: Float) {
        saved += speed
    }
}
