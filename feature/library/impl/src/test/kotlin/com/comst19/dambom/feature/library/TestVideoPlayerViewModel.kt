package com.comst19.dambom.feature.library

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.Player
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import com.comst19.dambom.core.domain.repository.PlaybackHistoryRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.feature.library.player.Media3VideoPlaybackController
import com.comst19.dambom.feature.library.player.VideoPlayerHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal class TestVideoPlayerViewModel(
    val host: VideoPlayerHost,
    val viewModel: VideoPlayerViewModel,
) {
    private val store =
        ViewModelStore().also { viewModelStore ->
            val factory =
                viewModelFactory {
                    initializer { host }
                    initializer { viewModel }
                }
            ViewModelProvider.create(viewModelStore, factory)[VideoPlayerHost::class]
            ViewModelProvider.create(viewModelStore, factory)[VideoPlayerViewModel::class]
        }

    val player: Player
        get() = host.player

    fun close() {
        store.clear()
    }
}

internal fun testVideoPlayerHarness(
    context: Context,
    history: PlaybackHistoryRepository = LibraryTestPlaybackHistory(),
    settings: SettingsRepository = LibraryTestSettings,
    applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    session: PrivateVideoSession = PrivateVideoSession(),
): TestVideoPlayerViewModel =
    testVideoPlayerHarness(
        controller = Media3VideoPlaybackController(context),
        history = history,
        settings = settings,
        applicationScope = applicationScope,
        session = session,
    )

internal fun testVideoPlayerHarness(
    controller: Media3VideoPlaybackController,
    history: PlaybackHistoryRepository = LibraryTestPlaybackHistory(),
    settings: SettingsRepository = LibraryTestSettings,
    applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    session: PrivateVideoSession = PrivateVideoSession(),
): TestVideoPlayerViewModel {
    val host = VideoPlayerHost(controller)
    val viewModel =
        VideoPlayerViewModel(
            host.attachController(),
            history,
            settings,
            applicationScope,
            AppEventBus(),
            session,
        )
    return TestVideoPlayerViewModel(host, viewModel)
}
