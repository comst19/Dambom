package com.comst19.dambom.core.common.ui

sealed interface AsyncUiState<out T> {
    data object Loading : AsyncUiState<Nothing>

    data class Success<T>(
        val data: T,
    ) : AsyncUiState<T>

    data class Error(
        val message: UiText,
    ) : AsyncUiState<Nothing>
}
