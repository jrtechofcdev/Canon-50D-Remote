package com.example.core.delivery.network

import java.net.Inet4Address
import java.net.NetworkInterface

/** State of the Wi-Fi the clients join. */
sealed interface NetState {
    data object Off : NetState
    data object Starting : NetState

    /**
     * Up: clients reach the phone at [host]. [ssid] and [password] are known when the app made the
     * network (Wi-Fi Direct) or when the photographer typed them for the phone's router.
     */
    data class Up(val host: String, val ssid: String?, val password: String?, val label: String) : NetState

    data class Failed(val reason: String) : NetState
}

/** One way of putting the phone and the clients on the same network. */
interface DeliveryNetwork {
    val id: String
    val label: String

    /** Brings the network up; returns its state (Up or Failed). */
    suspend fun start(ssid: String, password: String): NetState

    suspend fun stop()

    /** Phones in the group, when the network can tell (Wi-Fi Direct); null otherwise. */
    suspend fun devices(): Int? = null
}

/** The phone's own IPv4 addresses, to find where the clients can reach it. */
object LocalAddresses {
    data class Address(val iface: String, val ip: String)

    fun all(): List<Address> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.map { Address(ni.name, it.hostAddress.orEmpty()) } }
    }.getOrDefault(emptyList())

    /** First address on an interface whose name starts with one of [prefixes], in that order. */
    fun find(prefixes: List<String>, exclude: (Address) -> Boolean = { false }): Address? {
        val list = all().filterNot(exclude)
        return prefixes.firstNotNullOfOrNull { p -> list.firstOrNull { it.iface.startsWith(p) } }
    }
}
