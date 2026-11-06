package com.hmx.arena.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/** What the OS currently knows about the default network. */
enum class NetworkAvailability {
    /** A validated default network with internet access. */
    Available,

    /** No active default network (Wi-Fi and mobile data both off). */
    NoNetwork,

    /** A network exists but the OS has not validated internet access. */
    NoInternet,
}

/**
 * Reports real internet capability, not merely "a network is connected".
 *
 * [NetworkCapabilities.NET_CAPABILITY_VALIDATED] is the key signal: a hotel
 * Wi-Fi portal or a mobile network with no data path reports INTERNET but not
 * VALIDATED, which is exactly the "connected, no internet" case.
 */
@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun current(): NetworkAvailability {
        val cm = manager ?: return NetworkAvailability.NoNetwork
        val network = cm.activeNetwork ?: return NetworkAvailability.NoNetwork
        val capabilities = cm.getNetworkCapabilities(network) ?: return NetworkAvailability.NoNetwork
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkAvailability.NoNetwork
        }
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return NetworkAvailability.NoInternet
        }
        return NetworkAvailability.Available
    }

    /**
     * Event-driven connectivity changes. There is no polling; callers decide
     * when to run a backend health check. The default-network callback fires on
     * a binder thread, so the flow is conflated before it reaches the UI.
     */
    fun observe(): Flow<NetworkAvailability> = callbackFlow {
        val cm = manager
        if (cm == null) {
            trySend(NetworkAvailability.NoNetwork)
            awaitClose { }
            return@callbackFlow
        }
        trySend(current())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(current())
            }

            override fun onLost(network: Network) {
                trySend(current())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(current())
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged().conflate()
}
