package pt.isec.amov.safetysec.managers

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices
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

    //funcao p obter a localização para o botão de pânico
    //devolve null se não conseguir encontrar ou se o gps estiver desligado/sem permissão
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return try {
            val location = client.lastLocation.await() //tenta obter a ultima localização conhecida
            location
        }catch (e: Exception){
            e.printStackTrace()
            null
        }
    }

    //funcao p obter a loc em tempo real
    //devolve um fluxo continuo de dados
     @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long = 5000L): Flow<LocationData>{
        return callbackFlow {
            //cfg do pedido
            val request = LocationRequest.Builder (
                Priority.PRIORITY_HIGH_ACCURACY,
                intervalMs
            ).apply {
                setMinUpdateDistanceMeters(5f) //só enviamos notificação se ele se mover 5metros
            }.build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {

                    result.lastLocation?.let { location ->
                        // O Android dá a velocidade em m/s, convertemos para km/h
                        val speed = if (location.hasSpeed()) location.speed * 3.6 else 0.0

                        // Envia os dados
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

            // Inicia a escuta
            client.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )

            // Quando deixarmos de ouvir (cancelar o Flow), isto limpa a memória
            awaitClose {
                client.removeLocationUpdates(locationCallback)
            }

        }
    }
}