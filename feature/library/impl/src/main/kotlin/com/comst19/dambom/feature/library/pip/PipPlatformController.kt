package com.comst19.dambom.feature.library.pip

import androidx.compose.ui.unit.IntRect
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask

internal data class PipPlatformParams(
    val enabled: Boolean,
    val autoEnterEnabled: Boolean,
    val aspectRatio: PipAspectRatio? = null,
    val sourceBounds: IntRect? = null,
    val seamlessResizeEnabled: Boolean = false,
) {
    companion object {
        val Disabled = PipPlatformParams(enabled = false, autoEnterEnabled = false)
    }
}

internal data class PipPlatformState(
    val eligibility: PipEligibility,
    val aspectRatio: PipAspectRatio? = null,
    val sourceBounds: IntRect? = null,
)

internal interface PipPlatform {
    val apiLevel: Int

    fun setParams(params: PipPlatformParams)

    fun enterPictureInPictureMode()
}

internal class PipPlatformController(
    private val platform: PipPlatform,
) {
    private var state = PipPlatformState(eligibility = PipEligibility(false, false, false, false))

    fun update(state: PipPlatformState) {
        this.state = state
        platform.setParams(state.toParams(platform.apiLevel))
    }

    fun onUserLeaveHint() {
        if (platform.apiLevel in 26..30 && state.eligibility.isEligible) {
            platform.enterPictureInPictureMode()
        }
    }

    fun dispose() {
        platform.setParams(PipPlatformParams.Disabled)
    }
}

internal fun pipPlatformState(
    isSystemPipSupported: Boolean,
    isFullscreen: Boolean,
    isPlaying: Boolean,
    task: DownloadTask?,
    aspectRatio: PipAspectRatio?,
    sourceBounds: IntRect?,
): PipPlatformState =
    PipPlatformState(
        eligibility =
            PipEligibility(
                isSystemPipSupported = isSystemPipSupported,
                isFullscreen = isFullscreen,
                isPlaying = isPlaying,
                hasCompletedLocalVideo = task?.status == DownloadStatus.COMPLETED && !task.localFilePath.isNullOrBlank(),
            ),
        aspectRatio = aspectRatio,
        sourceBounds = sourceBounds,
    )

internal fun isVideoOnlyPipContent(
    isInPictureInPictureMode: Boolean,
    isTransitioningToPip: Boolean,
): Boolean = isInPictureInPictureMode || isTransitioningToPip

private fun PipPlatformState.toParams(apiLevel: Int): PipPlatformParams {
    if (!eligibility.isEligible) return PipPlatformParams.Disabled

    return PipPlatformParams(
        enabled = true,
        autoEnterEnabled = apiLevel >= 31,
        aspectRatio = aspectRatio,
        sourceBounds = sourceBounds,
        seamlessResizeEnabled = true,
    )
}
