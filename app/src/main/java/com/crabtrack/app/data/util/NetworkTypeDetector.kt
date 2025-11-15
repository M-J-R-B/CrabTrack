package com.crabtrack.app.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network types for data usage optimization
 */
enum class NetworkType {
    WIFI,           // WiFi connection - high bandwidth, no data limits
    MOBILE_DATA,    // Mobile data - limited, metered connection
    ROAMING,        // Roaming mobile data - expensive
    ETHERNET,       // Wired ethernet - treat like WiFi
    NONE            // No connection
}

/**
 * Detects and monitors network type changes for data optimization
 */
@Singleton
class NetworkTypeDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Get current network type
     */
    fun getCurrentNetworkType(): NetworkType {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // Check if roaming
                if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) {
                    NetworkType.ROAMING
                } else {
                    NetworkType.MOBILE_DATA
                }
            }
            else -> NetworkType.NONE
        }
    }

    /**
     * Check if connection is metered (mobile data or roaming)
     */
    fun isMeteredConnection(): Boolean {
        return connectivityManager.isActiveNetworkMetered
    }

    /**
     * Check if currently on WiFi or Ethernet (unmetered, high bandwidth)
     */
    fun isOnWiFi(): Boolean {
        val type = getCurrentNetworkType()
        return type == NetworkType.WIFI || type == NetworkType.ETHERNET
    }

    /**
     * Flow that emits network type changes
     */
    fun observeNetworkType(): Flow<NetworkType> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentNetworkType())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(getCurrentNetworkType())
            }

            override fun onLost(network: Network) {
                trySend(NetworkType.NONE)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit current state immediately
        trySend(getCurrentNetworkType())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    /**
     * Get recommended quality for current network
     */
    fun getRecommendedQuality(): String {
        return when (getCurrentNetworkType()) {
            NetworkType.WIFI, NetworkType.ETHERNET -> "HIGH"
            NetworkType.MOBILE_DATA -> "ULTRA_LOW"
            NetworkType.ROAMING -> "ULTRA_LOW"
            NetworkType.NONE -> "ULTRA_LOW"
        }
    }
}
