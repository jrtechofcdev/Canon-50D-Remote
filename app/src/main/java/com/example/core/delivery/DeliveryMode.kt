package com.example.core.delivery

import androidx.datastore.preferences.core.Preferences
import com.example.core.data.SettingKeys
import com.example.core.data.Settings

/** What one delivery tool reads from Ajustes. */
data class DeliveryConfig(
    val enabled: Boolean,
    val network: String,
    val ssid: String,
    val password: String,
    /** "light", "edited" or "original". */
    val size: String,
    val watermark: Boolean,
    val photographer: String,
    val picks: Boolean,
    val clearOnStop: Boolean,
    val folder: String?,
    val validityHours: Int,
    val auto: Boolean,
)

/**
 * The two delivery tools. They share the code but nothing else: each has its own port, page, network
 * settings, files and clients, so one never needs the other and a client of one never lands in the other.
 * Only one can hold the Wi-Fi at a time, so starting one stops the other.
 */
enum class DeliveryMode(
    val port: Int,
    /** Prepared files: cacheDir/<dir>/<token>/. */
    val dir: String,
    /** The page served to clients: assets/<assets>/. */
    val assets: String,
    val tag: String,
    val label: String,
    val passwordKey: Preferences.Key<String>,
) {
    Normal(8080, "delivery", "portal", "ENTREGA", "Entrega local", SettingKeys.deliveryPassword),
    Express(8090, "express", "express", "EXPRESS", "Fotografia Express", SettingKeys.expressPassword);

    val express: Boolean get() = this == Express

    fun config(s: Settings): DeliveryConfig = when (this) {
        Normal -> DeliveryConfig(
            enabled = s.deliveryEnabled, network = s.deliveryNetwork, ssid = s.deliverySsid, password = s.deliveryPassword,
            size = s.deliverySize, watermark = s.deliveryWatermark, photographer = s.deliveryPhotographer, picks = s.deliveryPicks,
            clearOnStop = s.deliveryClearOnStop, folder = s.deliveryFolder, validityHours = s.deliveryValidityHours,
            auto = s.deliveryMode == "auto",
        )
        // Express ends a client with "Finalizar e limpar", so its link has no clock of its own.
        Express -> DeliveryConfig(
            enabled = s.expressEnabled, network = s.expressNetwork, ssid = s.expressSsid, password = s.expressPassword,
            size = s.expressSize, watermark = s.expressWatermark, photographer = s.expressPhotographer, picks = s.expressPicks,
            clearOnStop = false, folder = s.expressFolder, validityHours = 0, auto = s.expressAuto,
        )
    }
}
