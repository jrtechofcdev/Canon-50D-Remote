package com.example.core.delivery.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import com.example.core.delivery.DeliveryRules
import com.example.core.log.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * A Wi-Fi Direct group owned by the phone, with the name and password from Ajustes (Android 10+).
 * Other phones, iPhones included, see it as a normal Wi-Fi network; the phone is always 192.168.49.1.
 * The permissions (Dispositivos por perto on Android 13+, location before) are asked by the screen
 * that starts the delivery.
 */
class WifiDirectNetwork(private val context: Context) : DeliveryNetwork {
    override val id = "direct"
    override val label = "Wi-Fi Direct"

    private val manager: WifiP2pManager? get() = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private fun channel(m: WifiP2pManager): WifiP2pManager.Channel =
        channel ?: m.initialize(context, Looper.getMainLooper(), null).also { channel = it }

    @SuppressLint("MissingPermission")
    override suspend fun start(ssid: String, password: String): NetState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return NetState.Failed("Wi-Fi Direct com nome fixo precisa do Android 10 ou mais novo. Use o roteador do celular.")
        val m = manager ?: return NetState.Failed("Este celular não tem Wi-Fi Direct. Use o roteador do celular.")
        val ch = channel(m)
        // A group left from a previous run (or another app) would keep its own name.
        call { l -> m.removeGroup(ch, l) }
        delay(300)
        val config = WifiP2pConfig.Builder()
            .setNetworkName(ssid)
            .setPassphrase(password)
            .setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_2GHZ)
            .enablePersistentMode(false)
            .build()
        config.wps.setup = WpsInfo.PBC
        val error = call { l -> m.createGroup(ch, config, l) }
        if (error != null) return NetState.Failed(reason(error))
        // The group takes a moment to get its address.
        val group = withTimeoutOrNull(8_000) {
            var g: WifiP2pGroup? = null
            while (g == null) {
                g = groupInfo(m, ch)?.takeIf { it.isGroupOwner }
                if (g == null) delay(400)
            }
            g
        } ?: return NetState.Failed("O Wi-Fi Direct não respondeu. Confira se o Wi-Fi está ligado e tente de novo.")
        val host = LocalAddresses.all().firstOrNull { it.iface == group.`interface` }?.ip ?: DeliveryRules.DIRECT_HOST
        AppLog.i("ENTREGA", "Wi-Fi Direct ${group.networkName} em $host (${group.`interface`})")
        return NetState.Up(host, group.networkName ?: ssid, group.passphrase ?: password, label)
    }

    @SuppressLint("MissingPermission")
    override suspend fun stop() {
        val m = manager ?: return
        channel?.let { ch -> call { l -> m.removeGroup(ch, l) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) channel?.close()
        channel = null
    }

    @SuppressLint("MissingPermission")
    override suspend fun devices(): Int? {
        val m = manager ?: return null
        val ch = channel ?: return null
        return groupInfo(m, ch)?.clientList?.size
    }

    @SuppressLint("MissingPermission")
    private suspend fun groupInfo(m: WifiP2pManager, ch: WifiP2pManager.Channel): WifiP2pGroup? =
        withTimeoutOrNull(3_000) { suspendCancellableCoroutine { c -> m.requestGroupInfo(ch) { g -> if (c.isActive) c.resume(g) } } }

    /** Runs one Wi-Fi Direct call; null on success, else the Android reason code. */
    private suspend fun call(block: (WifiP2pManager.ActionListener) -> Unit): Int? {
        val result = withTimeoutOrNull(6_000) {
            suspendCancellableCoroutine { c ->
                block(object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { if (c.isActive) c.resume(OK) }
                    override fun onFailure(reason: Int) { if (c.isActive) c.resume(reason) }
                })
            }
        } ?: TIMEOUT
        return result.takeUnless { it == OK }
    }

    private companion object {
        const val OK = Int.MIN_VALUE
        const val TIMEOUT = -1
    }

    private fun reason(code: Int) = when (code) {
        TIMEOUT -> "O Wi-Fi Direct não respondeu. Confira se o Wi-Fi está ligado e tente de novo."
        WifiP2pManager.P2P_UNSUPPORTED -> "Este celular não tem Wi-Fi Direct. Use o roteador do celular."
        WifiP2pManager.BUSY -> "O Wi-Fi está ocupado. Desligue e ligue o Wi-Fi e tente de novo."
        else -> "Não deu para criar a rede (código $code). Confira se o Wi-Fi e a localização estão ligados."
    }
}
