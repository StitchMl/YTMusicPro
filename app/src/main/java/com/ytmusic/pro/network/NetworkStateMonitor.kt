package com.ytmusic.pro.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

class NetworkStateMonitor(context: Context, private val listener: Listener) {

    interface Listener {
        fun onNetworkAvailable()
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (connectivityManager == null || networkCallback != null) {
            return
        }

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                listener.onNetworkAvailable()
            }
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
        } catch (_: Exception) {
            networkCallback = null
        }
    }

    fun stop() {
        val manager = connectivityManager ?: return
        val callback = networkCallback ?: return
        try {
            manager.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // Ignore cleanup issues if the callback was already removed.
        } finally {
            networkCallback = null
        }
    }
}
