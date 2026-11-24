package com.comst19.dambom.feature.home

import com.comst19.dambom.core.domain.model.DownloadOverview
import com.comst19.dambom.feature.home.contract.HomeDownloadSummary

internal fun toHomeDownloadSummary(overview: DownloadOverview): HomeDownloadSummary =
    HomeDownloadSummary(
        activeCount = overview.activeCount,
        pausedCount = overview.pausedCount,
        failedCount = overview.failedCount,
        progress = overview.progress,
    )
