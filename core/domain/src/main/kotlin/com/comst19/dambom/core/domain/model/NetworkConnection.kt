package com.comst19.dambom.core.domain.model

enum class NetworkConnection {
    OFFLINE,
    METERED,
    UNMETERED,
}

data class NetworkAccessState(
    val connection: NetworkConnection = NetworkConnection.OFFLINE,
    val wifiOnlyDownloads: Boolean = false,
) {
    val canUseInternet: Boolean
        get() = connection != NetworkConnection.OFFLINE

    val canDownload: Boolean
        get() = canUseInternet && (!wifiOnlyDownloads || connection == NetworkConnection.UNMETERED)

    val restriction: NetworkRestriction?
        get() =
            when {
                !canUseInternet -> NetworkRestriction.OFFLINE
                !canDownload -> NetworkRestriction.UNMETERED_REQUIRED
                else -> null
            }
}

enum class NetworkRestriction {
    OFFLINE,
    UNMETERED_REQUIRED,
}
