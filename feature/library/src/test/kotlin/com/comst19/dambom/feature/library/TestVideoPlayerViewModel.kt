package com.comst19.dambom.feature.library

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.domain.repository.PlaybackHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal fun testVideoPlayerViewModel(
    context: Context,
    history: PlaybackHistoryRepository = LibraryTestPlaybackHistory(),
) = VideoPlayerViewModel(
    context,
    history,
    LibraryTestSettings,
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    AppEventBus(),
)

internal fun VideoPlayerViewModel.closeForTest() {
    val model = this
    val store = ViewModelStore()
    val factory = viewModelFactory { initializer { model } }
    ViewModelProvider.create(store, factory)[VideoPlayerViewModel::class]
    store.clear()
}
