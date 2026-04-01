package com.ytmusic.pro.jam

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object LocalNetworkAddressResolver {

    @JvmStatic
    fun findLocalIpv4Candidates(): List<Candidate> {
        val candidates = mutableListOf<Candidate>()

        val interfaces = try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
        } catch (_: Exception) {
            emptyList()
        }

        for (networkInterface in interfaces) {
            val interfaceName = networkInterface.name.orEmpty().lowercase()
            if (
                !networkInterface.isUp ||
                networkInterface.isLoopback ||
                networkInterface.isVirtual ||
                isIgnoredInterface(interfaceName)
            ) {
                continue
            }

            val addresses = Collections.list(networkInterface.inetAddresses)
            for (address in addresses) {
                if (address !is Inet4Address || address.isLoopbackAddress || address.isLinkLocalAddress) {
                    continue
                }

                val hostAddress = address.hostAddress?.trim().orEmpty()
                if (hostAddress.isBlank()) {
                    continue
                }

                candidates += Candidate(
                    address = hostAddress,
                    interfaceName = interfaceName,
                    score = scoreCandidate(interfaceName, hostAddress, address.isSiteLocalAddress),
                )
            }
        }

        return candidates
            .distinctBy { it.address }
            .sortedByDescending { it.score }
    }

    private fun scoreCandidate(
        interfaceName: String,
        hostAddress: String,
        isSiteLocal: Boolean,
    ): Int {
        var score = 0

        if (isSiteLocal) {
            score += 100
        }

        score += when {
            interfaceName.startsWith("wlan") -> 90
            interfaceName.startsWith("swlan") -> 90
            interfaceName.startsWith("ap") -> 85
            interfaceName.startsWith("rndis") -> 80
            interfaceName.startsWith("usb") -> 80
            interfaceName.startsWith("eth") -> 75
            interfaceName.startsWith("en") -> 70
            interfaceName.contains("wifi") -> 70
            interfaceName.contains("hotspot") -> 70
            interfaceName.contains("tether") -> 70
            interfaceName.startsWith("rmnet") -> -120
            interfaceName.startsWith("ccmni") -> -120
            interfaceName.startsWith("pdp") -> -120
            interfaceName.startsWith("tun") -> -150
            interfaceName.startsWith("ppp") -> -150
            interfaceName.startsWith("wg") -> -150
            interfaceName.contains("vpn") -> -150
            else -> 0
        }

        score += when {
            hostAddress.startsWith("192.168.") -> 50
            hostAddress.startsWith("10.") -> 40
            hostAddress.startsWith("172.") && isPrivate172(hostAddress) -> 45
            hostAddress.startsWith("100.") -> -40
            else -> 0
        }

        return score
    }

    private fun isIgnoredInterface(interfaceName: String): Boolean {
        return interfaceName == "lo" ||
            interfaceName.startsWith("dummy") ||
            interfaceName.startsWith("ifb") ||
            interfaceName.startsWith("ip6") ||
            interfaceName.startsWith("sit") ||
            interfaceName.startsWith("gre") ||
            interfaceName.startsWith("v4-rmnet") ||
            interfaceName.startsWith("bt-pan") ||
            interfaceName.startsWith("p2p")
    }

    private fun isPrivate172(hostAddress: String): Boolean {
        val secondOctet = hostAddress.split('.').getOrNull(1)?.toIntOrNull() ?: return false
        return secondOctet in 16..31
    }

    data class Candidate(
        val address: String,
        val interfaceName: String,
        val score: Int,
    )
}
