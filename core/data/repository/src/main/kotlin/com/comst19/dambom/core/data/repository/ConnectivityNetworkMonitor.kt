package com.comst19.dambom.core.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.core.domain.model.NetworkConnection
import com.comst19.dambom.core.domain.repository.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ConnectivityNetworkMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : NetworkMonitor {
        override val connection: Flow<NetworkConnection> =
            context.getSystemService<ConnectivityManager>()?.connectionFlow()
                ?: flowOf(NetworkConnection.OFFLINE)

        private fun ConnectivityManager.connectionFlow(): Flow<NetworkConnection> =
            callbackFlow {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            trySend(networkCapabilities.toConnection())
                        }

                        override fun onLost(network: Network) {
                            trySend(NetworkConnection.OFFLINE)
                        }
                    }
                registerDefaultNetworkCallback(callback)
                trySend(getNetworkCapabilities(activeNetwork).toConnection())
                awaitClose { unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()
                .conflate()
                .flowOn(ioDispatcher)
    }

internal fun NetworkCapabilities?.toConnection(): NetworkConnection {
    if (this == null ||
        !hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
        !hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    ) {
        return NetworkConnection.OFFLINE
    }
    return if (hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
        NetworkConnection.UNMETERED
    } else {
        NetworkConnection.METERED
    }
}
