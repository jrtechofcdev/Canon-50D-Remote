package com.example.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Where the photos are being taken, from the phone's GPS chip (works with no signal or internet).
 * Runs only while the camera is connected and the option is on, to spare the battery.
 */
class LocationTracker(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    @Volatile private var last: Location? = null
    private var running = false

    private val listener = LocationListener { last = it }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !hasPermission()) return
        running = true
        // GPS works offline; the network provider only helps indoors when there is signal.
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            .forEach { provider ->
                runCatching { manager.getLastKnownLocation(provider)?.let(::offer) }
                runCatching { manager.requestLocationUpdates(provider, 60_000L, 25f, listener, Looper.getMainLooper()) }
            }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { manager.removeUpdates(listener) }
    }

    private fun offer(location: Location) {
        val current = last
        if (current == null || location.time > current.time) last = location
    }

    /** Latest fix if it is recent enough to describe where the photo was taken. */
    fun recent(maxAgeMs: Long = 5 * 60_000L): Pair<Double, Double>? {
        val l = last ?: return null
        if (System.currentTimeMillis() - l.time > maxAgeMs) return null
        return l.latitude to l.longitude
    }
}
