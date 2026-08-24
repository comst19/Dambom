package com.comst19.dambom.feature.home.contract

internal data class HomeUiState(
    val url: String = "",
    val isUrlValid: Boolean = false,
    val showClipboardConsent: Boolean = false,
    val clipboardSuggestionEnabled: Boolean = false,
    val clipboardUrl: String? = null,
    val sharedUrl: String? = null,
    val downloadSummary: HomeDownloadSummary = HomeDownloadSummary(),
)

internal data class HomeDownloadSummary(
    val activeCount: Int = 0,
    val pausedCount: Int = 0,
    val failedCount: Int = 0,
    val progress: Float = 0f,
) {
    val isVisible: Boolean
        get() = activeCount + pausedCount + failedCount > 0
}
