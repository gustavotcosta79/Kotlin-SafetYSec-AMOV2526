package pt.isec.amov.safetysec.managers

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource // IMPORTANTE
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class LocationData (
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val timeStamp: Long
)

class LocationManager(private val context: Context) {
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    // --- MUDANÇA AQUI ---
    // Em vez de pedir a "lastLocation" (memória), pedimos a "CurrentLocation" (fresca)
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return try {
            // Priority.PRIORITY_HIGH_ACCURACY exige GPS
            // CancellationTokenSource é necessário para cancelar se demorar muito (embora aqui não estejamos a usar timeout manual)
            val priority = Priority.PRIORITY_HIGH_ACCURACY
            val cancellationTokenSource = CancellationTokenSource()

            val location = client.getCurrentLocation(
                priority,
                cancellationTokenSource.token
            ).await()

            location
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    // --------------------

    // funcao p obter a loc em tempo real (Mantém-se igual)
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long = 5000L): Flow<LocationData>{
        return callbackFlow {
            val request = LocationRequest.Builder (
                Priority.PRIORITY_HIGH_ACCURACY,
                intervalMs
            ).apply {
                setMinUpdateDistanceMeters(5f)
            }.build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        val speed = if (location.hasSpeed()) location.speed * 3.6 else 0.0
                        trySend(
                            LocationData(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                speedKmh = speed,
                                timeStamp = location.time
                            )
                        )
                    }
                }
            }

            client.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )

            awaitClose {
                client.removeLocationUpdates(locationCallback)
            }
        }
    }
}