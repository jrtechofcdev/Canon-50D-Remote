package com.example.core.delivery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.core.log.AppLog
import java.io.File

/** "Ana gostou de uma foto": a notification with the photo, opening the tool the client is in. */
class LikeNotifier(private val context: Context) {

    fun liked(session: DeliverySession, item: DeliveryItem) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel()
        val who = session.client.takeUnless { it == LocalServer.EXPRESS_DEFAULT } ?: "O cliente"
        val tool = if (session.express) "Fotografia Express" else "Entrega local"
        val picture = item.path?.let { path ->
            val base = path.removeSuffix(".jpg")
            listOf("$base.thumb.jpg", "$base.pthumb.jpg", path).map(::File).firstOrNull { it.exists() }
                ?.let { runCatching { BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = if (it.path == path) 4 else 1 }) }.getOrNull() }
        }
        val open = PendingIntent.getActivity(
            context, item.id.toInt(),
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_DELIVERY)
                .putExtra(MainActivity.EXTRA_ROUTE, if (session.express) "express" else "delivery"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.example.R.drawable.ic_stat_symbol)
            .setColor(0xFF00FCA8.toInt())
            .setContentTitle("$who gostou de uma foto ♥")
            .setContentText("${item.name} · $tool")
            .setAutoCancel(true)
            .setContentIntent(open)
            .setGroup("likes_${session.id}")
            .apply {
                picture?.let {
                    setLargeIcon(it)
                    setStyle(NotificationCompat.BigPictureStyle().bigPicture(it).bigLargeIcon(null as android.graphics.Bitmap?))
                }
            }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(BASE_ID + item.id.toInt(), n) }
            .onFailure { AppLog.w("ENTREGA", "Aviso de curtida não saiu", it) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "Curtidas dos clientes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Quando um cliente marca uma foto com o coração"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private companion object {
        const val CHANNEL = "likes"
        const val BASE_ID = 10_000
    }
}
