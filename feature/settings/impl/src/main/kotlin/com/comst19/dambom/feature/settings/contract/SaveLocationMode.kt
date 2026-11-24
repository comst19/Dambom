package com.comst19.dambom.feature.settings.contract

internal enum class SaveLocationMode(
    val usesConfiguredFolder: Boolean,
) {
    DEFAULT_FOLDER(true),
    CHOOSE_EACH_TIME(false),
    ;

    companion object {
        fun from(usesConfiguredFolder: Boolean): SaveLocationMode = if (usesConfiguredFolder) DEFAULT_FOLDER else CHOOSE_EACH_TIME
    }
}
