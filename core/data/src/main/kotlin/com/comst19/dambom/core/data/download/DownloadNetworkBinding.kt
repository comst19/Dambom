package com.comst19.dambom.core.data.download

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory

internal class MissingJobNetworkException(
    message: String = "Job network unavailable",
) : IOException(message)

internal interface DownloadNetworkBinding {
    val socketFactory: SocketFactory

    fun getAllByName(hostname: String): Array<InetAddress>

    fun isAvailableFor(wifiOnlyDownloads: Boolean): Boolean = true
}

internal fun Network.asDownloadNetworkBinding(connectivity: ConnectivityManager? = null): DownloadNetworkBinding =
    PlatformDownloadNetworkBinding(this, connectivity)

private class PlatformDownloadNetworkBinding(
    private val network: Network,
    private val connectivityManager: ConnectivityManager?,
) : DownloadNetworkBinding {
    override val socketFactory: SocketFactory
        get() = network.socketFactory

    override fun getAllByName(hostname: String): Array<InetAddress> = network.getAllByName(hostname)

    override fun isAvailableFor(wifiOnlyDownloads: Boolean): Boolean {
        val manager = connectivityManager ?: return true
        val capabilities = manager.getNetworkCapabilities(network)
        val hasValidatedInternet =
            capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return hasValidatedInternet &&
            (!wifiOnlyDownloads || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    }

    override fun equals(other: Any?): Boolean = other is PlatformDownloadNetworkBinding && other.network == network

    override fun hashCode(): Int = network.hashCode()
}

internal class NetworkGeneration(
    val binding: DownloadNetworkBinding?,
    val connectionPool: ConnectionPool = ConnectionPool(),
) {
    val socketFactory: SocketFactory = NetworkBoundSocketFactory(binding)
    val dns: Dns = NetworkBoundDns(binding)
}

internal class DownloadNetworkSession(
    initialNetwork: DownloadNetworkBinding? = null,
) {
    private val updateLock = Any()
    private val current = AtomicReference(NetworkGeneration(initialNetwork))

    fun currentBinding(): DownloadNetworkBinding? = current.get().binding

    internal fun currentGeneration(): NetworkGeneration = current.get()

    fun update(
        next: DownloadNetworkBinding?,
        evictExecutor: (() -> Unit) -> Unit = { it() },
    ): Boolean {
        val previous =
            synchronized(updateLock) {
                val existing = current.get()
                if (existing.binding == next) return false
                current.set(NetworkGeneration(next))
                existing
            }
        evictExecutor { previous.connectionPool.evictAll() }
        return true
    }

    fun evictAll(evictExecutor: (() -> Unit) -> Unit = { it() }) {
        val pool = current.get().connectionPool
        evictExecutor { pool.evictAll() }
    }
}

internal fun OkHttpClient.bindToNetworkCallFactory(networkSession: DownloadNetworkSession?): Call.Factory =
    if (networkSession == null) {
        this
    } else {
        NetworkSessionCallFactory(baseClient = this, networkSession = networkSession)
    }

private class NetworkSessionCallFactory(
    private val baseClient: OkHttpClient,
    private val networkSession: DownloadNetworkSession,
) : Call.Factory {
    private val cachedClient = AtomicReference<Pair<NetworkGeneration, OkHttpClient>?>()

    override fun newCall(request: Request): Call = clientFor(networkSession.currentGeneration()).newCall(request)

    private fun clientFor(generation: NetworkGeneration): OkHttpClient {
        val cached = cachedClient.get()
        if (cached != null && cached.first === generation) return cached.second
        val derived =
            baseClient
                .newBuilder()
                .connectionPool(generation.connectionPool)
                .socketFactory(generation.socketFactory)
                .dns(generation.dns)
                .eventListenerFactory {
                    RetiredPoolCleanupEventListener(networkSession, generation)
                }.build()
        cachedClient.compareAndSet(cached, generation to derived)
        return cachedClient.get()?.takeIf { it.first === generation }?.second ?: derived
    }
}

private class RetiredPoolCleanupEventListener(
    private val networkSession: DownloadNetworkSession,
    private val generation: NetworkGeneration,
) : EventListener() {
    override fun callEnd(call: Call) {
        evictIfRetired()
    }

    override fun callFailed(
        call: Call,
        ioe: IOException,
    ) {
        evictIfRetired()
    }

    private fun evictIfRetired() {
        if (networkSession.currentGeneration() !== generation) {
            generation.connectionPool.evictAll()
        }
    }
}

private class NetworkBoundSocketFactory(
    private val binding: DownloadNetworkBinding?,
) : SocketFactory() {
    private fun delegate(): SocketFactory = binding?.socketFactory ?: throw MissingJobNetworkException()

    override fun createSocket(): Socket = delegate().createSocket()

    override fun createSocket(
        host: String,
        port: Int,
    ): Socket = delegate().createSocket(host, port)

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = delegate().createSocket(host, port, localHost, localPort)

    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket = delegate().createSocket(host, port)

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket = delegate().createSocket(address, port, localAddress, localPort)
}

private class NetworkBoundDns(
    private val binding: DownloadNetworkBinding?,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        binding?.getAllByName(hostname)?.toList() ?: throw MissingJobNetworkException()
}
