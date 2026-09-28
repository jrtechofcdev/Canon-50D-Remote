package com.example.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.TetherApp
import com.example.core.camera.ConnectionState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the tether alive with the screen off: a foreground notification plus a partial wake lock,
 * so event polling and downloads continue while the phone sits in a pocket or on a stand.
 */
class CameraForegroundService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Conectando à câmera…"), type)

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jrlink:session")
            .apply { acquire(6 * 60 * 60 * 1000L) }

        val app = application as TetherApp
        lifecycleScope.launch {
            combine(app.camera.state, app.tether.receivedThisSession, app.tether.transfer) { state, count, transfer ->
                Triple(state, count, transfer)
            }.collect { (state, count, transfer) ->
                when (state) {
                    is ConnectionState.Connected -> {
                        val text = transfer?.let { "Recebendo ${it.fileName} · ${(it.progress * 100).toInt()}%" }
                            ?: "$count ${if (count == 1) "foto recebida" else "fotos recebidas"}"
                        update("${state.model} · $text")
                    }
                    ConnectionState.Connecting, ConnectionState.WaitingPermission -> update("Conectando à câmera…")
                    else -> stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(com.example.R.drawable.ic_stat_symbol)
        .setContentTitle("JR C50D Sync · captura vinculada")
        .setColor(0xFF00FCA8.toInt())
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Captura vinculada", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 50
        private const val CHANNEL_ID = "tether"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, CameraForegroundService::class.java)) }
        }
    }
}
