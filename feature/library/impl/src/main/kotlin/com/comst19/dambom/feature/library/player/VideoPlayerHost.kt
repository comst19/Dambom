package com.comst19.dambom.feature.library.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
internal class VideoPlayerHost
    internal constructor(
        private val media3Controller: Media3VideoPlaybackController,
    ) : ViewModel() {
        @Inject
        constructor(
            @ApplicationContext context: Context,
        ) : this(Media3VideoPlaybackController(context))

        private var attachedToViewModel = false

        val player: Player
            get() = media3Controller.player

        fun attachController(): VideoPlaybackController {
            attachedToViewModel = true
            return media3Controller
        }

        override fun onCleared() {
            if (!attachedToViewModel) {
                media3Controller.release()
            }
        }
    }
