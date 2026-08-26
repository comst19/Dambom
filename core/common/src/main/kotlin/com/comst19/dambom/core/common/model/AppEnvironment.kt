package com.comst19.dambom.core.common.model

enum class AppEnvironment {
    DEBUG,
    QA,
    RELEASE,
    ;

    val isDebug: Boolean
        get() = this == DEBUG

    val isRelease: Boolean
        get() = this == RELEASE

    companion object {
        fun from(buildConfigValue: String): AppEnvironment = valueOf(buildConfigValue)
    }
}
