package com.comst19.dambom.feature.detection.component

import androidx.lifecycle.ViewModel
import com.comst19.dambom.core.common.net.MediaHeaderPolicy
import com.comst19.dambom.core.common.ui.player.MediaPreviewDataSourceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class DetectionPreviewHost
    @Inject
    constructor(
        val previewDataSourceFactory: MediaPreviewDataSourceFactory,
        val headerPolicy: MediaHeaderPolicy,
    ) : ViewModel()
