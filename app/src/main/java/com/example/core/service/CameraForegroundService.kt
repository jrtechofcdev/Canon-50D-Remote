package com.example.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.Canon50DRemoteApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CameraForegroundService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val NOTIFICATION_ID = 2002
    private val CHANNEL_ID = "camera_foreground"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundServiceCompat()

        val app = application as Canon50DRemoteApplication
        val cameraManager = app.cameraManager
        val downloadManager = app.downloadManager

        // Listen for connection changes to update the notification message
        scope.launch {
            cameraManager.connectionState.collect { state ->
                val stateStr = when (state) {
                    com.example.core.camera.CameraManager.ConnectionState.CONNECTED_REAL_USB -> "Conectado via USB OTG"
                    com.example.core.camera.CameraManager.ConnectionState.CONNECTED_SIMULATOR -> "Modo Simulador Ativo"
                    else -> "Câmera Desconectada"
                }
                updateNotification("Canon 50D Remote: $stateStr")
            }
        }

        // Listen to download tasks to show active progress in notifications
        scope.launch {
            downloadManager.currentTask.collect { task ->
                if (task != null && task.isDownloading) {
                    val percent = (task.progress * 100).toInt()
                    updateNotification("Baixando ${task.filename} - $percent% (${String.format("%.1f", task.speedMbS)} MB/s)")
                } else {
                    val state = cameraManager.connectionState.value
                    val stateStr = when (state) {
                        com.example.core.camera.CameraManager.ConnectionState.CONNECTED_REAL_USB -> "Conectado via USB OTG"
                        com.example.core.camera.CameraManager.ConnectionState.CONNECTED_SIMULATOR -> "Modo Simulador Ativo"
                        else -> "Câmera Desconectada"
                    }
                    updateNotification("Canon 50D Remote: $stateStr")
                }
            }
        }
    }

    private fun startForegroundServiceCompat() {
        val notification = buildNotification("Aguardando conexões da Canon 50D...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Canon 50D Remote")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Canal de Conexão USB",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        serviceJob.cancel()
    }
}
