package com.comst19.dambom.feature.web

import android.os.Bundle
import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.domain.model.MediaResourceRequest

@Immutable
internal data class WebScreenCallbacks(
    val onBack: () -> Unit = {},
    val onNavigate: (String) -> Unit = {},
    val onPageStarted: (Long, String?, String?, Long) -> Unit = { _, _, _, _ -> },
    val onPageFinished: (Long, String?, String?, Long) -> Unit = { _, _, _, _ -> },
    val onPageChanged: (Long, String?, String?) -> Unit = { _, _, _ -> },
    val onMediaRequest: (Long, Long, MediaResourceRequest) -> Unit = { _, _, _ -> },
    val onScan: () -> Unit = {},
    val onOpenDetectedMedia: () -> Unit = {},
    val onCreateTab: (String?) -> Unit = {},
    val onSelectTab: (Long) -> Unit = {},
    val onCloseTab: (Long) -> Unit = {},
    val savedWebState: (Long) -> Bundle? = { null },
    val onSaveWebState: (Long, Bundle) -> Unit = { _, _ -> },
    val consumedNavigationRequestId: (Long) -> Long = { 0L },
    val onNavigationRequestConsumed: (Long, Long) -> Unit = { _, _ -> },
    val onOpenExternal: (String) -> Unit = {},
    val onCopyLink: (String) -> Unit = {},
    val onShareLink: (String) -> Unit = {},
)
