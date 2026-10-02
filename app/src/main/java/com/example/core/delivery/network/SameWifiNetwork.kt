package com.example.core.delivery.network

/**
 * The venue's Wi-Fi: the phone and the clients are on the same router. The app only finds its
 * address there; the name and password of that Wi-Fi are the venue's, so there is no Wi-Fi QR.
 */
class SameWifiNetwork : DeliveryNetwork {
    override val id = "wifi"
    override val label = "Wi-Fi do local"

    override suspend fun start(ssid: String, password: String): NetState {
        val address = LocalAddresses.find(listOf("wlan0", "wlan", "eth"), exclude = { it.ip.startsWith("192.168.49.") })
            ?: return NetState.Failed("O celular não está em um Wi-Fi. Conecte no Wi-Fi do local e toque em Iniciar de novo.")
        return NetState.Up(address.ip, null, null, label)
    }

    override suspend fun stop() = Unit
}
