package com.satpam.piket.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.satpam.piket.data.model.LokasiKonfig
import kotlinx.coroutines.tasks.await

sealed class GeofenceCheckResult {
    /** Berada di dalam radius geofence yang diizinkan */
    data class Valid(val distanceMeters: Float, val latitude: Double, val longitude: Double) : GeofenceCheckResult()

    /** Di luar radius geofence (jarak melebihi batas yang ditentukan) */
    data class TooFar(val distanceMeters: Float, val radiusMeter: Int, val namaLokasi: String) : GeofenceCheckResult()

    /** Belum ada izin akses lokasi GPS */
    object NoPermission : GeofenceCheckResult()

    /** GPS perangkat tidak aktif atau tidak dapat membaca sinyal lokasi */
    object LocationUnavailable : GeofenceCheckResult()

    /** Koordinat lokasi di backend belum diset (0, 0), diizinkan atau dengan peringatan */
    data class Unconfigured(val namaLokasi: String, val latitude: Double, val longitude: Double) : GeofenceCheckResult()
}

class GeofenceManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) return null

        return try {
            val cancellationTokenSource = CancellationTokenSource()
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await()

            location ?: fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            Log.e("GeofenceManager", "Gagal mendapatkan lokasi GPS: ${e.message}")
            try {
                fusedLocationClient.lastLocation.await()
            } catch (ex: Exception) {
                null
            }
        }
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    suspend fun checkGeofence(lokasiKonfig: LokasiKonfig?): GeofenceCheckResult {
        if (!hasLocationPermission()) {
            return GeofenceCheckResult.NoPermission
        }

        val myLocation = getCurrentLocation() ?: return GeofenceCheckResult.LocationUnavailable

        val myLat = myLocation.latitude
        val myLng = myLocation.longitude

        if (lokasiKonfig == null) {
            return GeofenceCheckResult.Valid(0f, myLat, myLng)
        }

        val targetLat = lokasiKonfig.latitude
        val targetLng = lokasiKonfig.longitude
        val maxRadius = if (lokasiKonfig.radiusMeter > 0) lokasiKonfig.radiusMeter else 50

        // Jika koordinat belum diset di backend (0.0, 0.0)
        if (targetLat == 0.0 && targetLng == 0.0) {
            return GeofenceCheckResult.Unconfigured(lokasiKonfig.namaLokasi, myLat, myLng)
        }

        val distance = calculateDistance(myLat, myLng, targetLat, targetLng)

        return if (distance <= maxRadius) {
            GeofenceCheckResult.Valid(distance, myLat, myLng)
        } else {
            GeofenceCheckResult.TooFar(distance, maxRadius, lokasiKonfig.namaLokasi)
        }
    }
}
