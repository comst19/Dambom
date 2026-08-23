package com.comst19.dambom.core.common.ui

import android.content.Context

sealed interface UiText {
    data class Dynamic(
        val value: String,
    ) : UiText

    data class Resource(
        val id: Int,
    ) : UiText
}

fun UiText.resolve(context: Context): String =
    when (this) {
        is UiText.Dynamic -> value
        is UiText.Resource -> context.getString(id)
    }
