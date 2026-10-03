package com.example.core.delivery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.TetherApp
import com.example.core.log.AppLog
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the delivery alive with the screen off: a notification ("Entrega ativa · 2 clientes · 17 fotos")
 * with a Parar button, a wake lock and a Wi-Fi lock so the phone keeps answering the clients.
 */
class DeliveryService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Ligando a entrega…"), type)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jrlink:delivery")
            .apply { acquire(8 * 60 * 60 * 1000L) }
        wifiLock = runCatching {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(mode, "jrlink:delivery").apply { acquire() }
        }.getOrNull()

        val app = application as TetherApp
        lifecycleScope.launch {
            // Whichever tool holds the Wi-Fi (only one at a time).
            combine(line(app.delivery), line(app.express)) { a, b -> a ?: b }
                .distinctUntilChanged().collect { text -> if (text == null) stopSelf() else update(text) }
        }
    }

    private fun line(tool: LocalDelivery) =
        combine(tool.status, tool.online, tool.items, tool.sessions) { status, online, items, sessions ->
            if (status !is DeliveryStatus.Running) return@combine null
            val ready = items.count { it.state in DeliveryState.served }
            val people = if (tool.mode.express) {
                if (online > 0) "$online no portal" else "aguardando o cliente"
            } else {
                val clients = sessions.count { it.isOpen }
                if (online > 0) "$online ${if (online == 1) "celular" else "celulares"} no portal" else "$clients ${if (clients == 1) "cliente" else "clientes"}"
            }
            "${tool.mode.label} · $people · $ready ${if (ready == 1) "foto" else "fotos"}"
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            lifecycleScope.launch {
                val app = application as TetherApp
                app.delivery.stop()
                app.express.stop()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun update(text: String) {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text)) }
            .onFailure { AppLog.e("ENTREGA", "Notificação não atualizada", it) }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(com.example.R.drawable.ic_stat_symbol)
        .setContentTitle("Entrega no ar")
        .setColor(0xFF00FCA8.toInt())
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 1, Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_DELIVERY)
                    // Opens the tab of the tool that is on the air.
                    .putExtra(MainActivity.EXTRA_ROUTE, if ((application as TetherApp).express.status.value is DeliveryStatus.Running) "express" else "delivery"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(
            0, "Parar",
            PendingIntent.getService(this, 2, Intent(this, DeliveryService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE),
        )
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Entrega local", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 51
        private const val CHANNEL_ID = "delivery"
        private const val ACTION_STOP = "com.example.delivery.STOP"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DeliveryService::class.java)) }
                .onFailure { AppLog.w("ENTREGA", "Serviço da entrega não iniciou", it) }
        }
    }
}
