package com.ytmusic.pro.jam

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object LocalNetworkAddressResolver {

    @JvmStatic
    fun findLocalIpv4Address(): String? {
        val preferred = mutableListOf<String>()
        val fallback = mutableListOf<String>()

        val interfaces = try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
        } catch (_: Exception) {
            emptyList()
        }

        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) {
                continue
            }

            val addresses = Collections.list(networkInterface.inetAddresses)
            for (address in addresses) {
                if (address !is Inet4Address || address.isLoopbackAddress) {
                    continue
                }
                val hostAddress = address.hostAddress ?: continue
                if (address.isSiteLocalAddress) {
                    preferred += hostAddress
                } else {
                    fallback += hostAddress
                }
            }
        }

        return preferred.firstOrNull() ?: fallback.firstOrNull()
    }
}
