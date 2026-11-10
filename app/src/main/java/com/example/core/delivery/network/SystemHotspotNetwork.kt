package com.example.core.delivery.network

/**
 * The phone's own Wi-Fi router ("Roteador Wi-Fi" / "Ponto de acesso"), turned on by the photographer:
 * Android does not let an app turn it on with a fixed name. The app finds the router's address and uses
 * the name and password typed in Ajustes for the QR code, so they must match the phone's router.
 */
class SystemHotspotNetwork : DeliveryNetwork {
    override val id = "hotspot"
    override val label = "Roteador do celular"

    override suspend fun start(ssid: String, password: String): NetState {
        val address = LocalAddresses.find(listOf("swlan", "ap", "softap", "wlan1", "wlan2", "wigig", "wlan0"))
            ?: return NetState.Failed("Ligue o roteador Wi-Fi do celular (Configurações → Conexões → Roteador Wi-Fi) e toque em Iniciar de novo.")
        return NetState.Up(address.ip, ssid, password, label)
    }

    override suspend fun stop() = Unit
}
