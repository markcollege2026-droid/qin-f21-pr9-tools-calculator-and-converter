package com.qin.f21.toolbox.core.hardware

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

/**
 * GNSS tracking built on the platform [LocationManager].
 *
 * No Google Play services are used, so the toolbox works on any Android 11 device and
 * completely offline.
 */
class LocationTracker(
    context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onLocationChanged(location: Location, satellitesInView: Int, satellitesUsed: Int)
        fun onProviderState(provider: String, enabled: Boolean)
    }

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var running = false
    private var satellitesInView = 0
    private var satellitesUsed = 0

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            listener.onLocationChanged(location, satellitesInView, satellitesUsed)
        }

        override fun onProviderEnabled(provider: String) {
            listener.onProviderState(provider, true)
        }

        override fun onProviderDisabled(provider: String) {
            listener.onProviderState(provider, false)
        }

        @Deprecated("Required by the pre-API-30 LocationListener contract")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            satellitesInView = status.satelliteCount
            var used = 0
            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) used++
            }
            satellitesUsed = used
        }
    }

    val isRunning: Boolean get() = running

    val providers: List<String>
        get() = try {
            locationManager?.getProviders(true)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun start(): Boolean {
        val manager = locationManager ?: return false
        if (!hasPermission()) return false
        if (running) return true
        return try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (manager.getProvider(provider) != null) {
                    manager.requestLocationUpdates(provider, 1000L, 0f, locationListener, Looper.getMainLooper())
                    listener.onProviderState(provider, true)
                }
            }
            manager.registerGnssStatusCallback(gnssCallback, android.os.Handler(Looper.getMainLooper()))
            running = true
            true
        } catch (e: Exception) {
            running = false
            false
        }
    }

    fun stop() {
        val manager = locationManager ?: return
        try {
            manager.removeUpdates(locationListener)
            manager.unregisterGnssStatusCallback(gnssCallback)
        } catch (e: Exception) {
            // ignore
        }
        running = false
    }

    fun lastKnownLocation(): Location? = try {
        locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    } catch (e: Exception) {
        null
    }
}
