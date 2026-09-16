package com.vaani.data.device.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Joins the wearable's Wi-Fi SoftAP FROM THE APP (never the host) using
 * [WifiNetworkSpecifier] (API 29+). The framework shows a one-time system dialog
 * asking the user to approve joining "Vaani-XXXX"; on approval the app process is
 * bound to that AP network so OkHttp reaches http://192.168.4.1. Only THIS app's
 * traffic is routed to the AP — the rest of the phone keeps its normal network.
 */
@Singleton
class WifiApConnector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile var boundNetwork: Network? = null
        private set

    /** Notified when the bound AP network drops (onLost) so the link can mark itself disconnected. */
    @Volatile var onNetworkLost: (() -> Unit)? = null

    fun isConnected(): Boolean = boundNetwork != null

    /**
     * Request the AP network and return the bound [Network] (or null). We do NOT call
     * bindProcessToNetwork — that routes the WHOLE app process to an internet-less AP and, if
     * not perfectly torn down, leaves the app with no connectivity. Instead the caller routes
     * ONLY wearable HTTP through [Network.getSocketFactory] on a dedicated OkHttp client, so the
     * rest of the app keeps its normal network.
     */
    suspend fun connect(ssid: String, passphrase: String, timeoutMs: Long = 25_000): Network? {
        disconnect()
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(passphrase)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()
        val deferred = CompletableDeferred<Network?>()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                boundNetwork = network
                if (!deferred.isCompleted) deferred.complete(network)
            }
            override fun onUnavailable() { if (!deferred.isCompleted) deferred.complete(null) }
            override fun onLost(network: Network) {
                if (boundNetwork == network) {
                    boundNetwork = null
                    onNetworkLost?.invoke()
                }
            }
        }
        callback = cb
        try {
            cm.requestNetwork(request, cb, timeoutMs.toInt())
        } catch (t: Throwable) {
            return null
        }
        return withTimeoutOrNull(timeoutMs + 2_000) { deferred.await() }
    }

    /** Release the AP network request, restoring normal routing. Safe to call repeatedly. */
    fun disconnect() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        boundNetwork = null
    }
}
