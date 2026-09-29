package com.comst19.dambom.feature.library.trim.contract

internal data class TrimSelection(
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationMillis: Long get() = endMillis - startMillis

    fun isValidFor(duration: Long): Boolean = startMillis >= 0L && endMillis <= duration && endMillis > startMillis
}
