package pt.isec.amov.safetysec.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager as AndroidSensorManager
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SensorManager(private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as AndroidSensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Valores de referência (podes ajustar nos testes)
    // Gravidade normal é ~9.8 m/s²
//    private val FALL_THRESHOLD = 2.0 // Quase 0G (queda livre)
//    private val IMPACT_THRESHOLD = 25.0 // Impacto forte (queda ou acidente)
//    private val ACCIDENT_THRESHOLD = 40.0 // Desaceleração brutal (acidente carro)

    // Usa estes para testar:
    private val FALL_THRESHOLD = 9.0     // Fácil de atingir (a gravidade é 9.8)
    private val IMPACT_THRESHOLD = 12.0  // Basta um pequeno abanão
    private val ACCIDENT_THRESHOLD = 15.0 // Basta um abanão médio

    // Devolve um Flow com eventos: "QUEDA" ou "ACIDENTE"
    fun getAccelerationEvents(): Flow<String> {
        return callbackFlow {
            val listener = object : SensorEventListener {
                var wasInFreeFall = false
                var lastUpdate = System.currentTimeMillis()

                override fun onSensorChanged(event: SensorEvent?) {
                    event ?: return

                    // Limita a leitura para não sobrecarregar (ex: a cada 100ms)
                    val curTime = System.currentTimeMillis()
                    if ((curTime - lastUpdate) < 100) return
                    lastUpdate = curTime

                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    // Calcula a magnitude da aceleração (vetor total)
                    val acceleration = sqrt((x*x + y*y + z*z).toDouble())

                    // Lógica Simplificada de Deteção

                    // 1. Deteção de ACIDENTE (Força G muito alta imediata)
                    if (acceleration > ACCIDENT_THRESHOLD) {
                        trySend("ACIDENTE")
                        wasInFreeFall = false
                        return
                    }

                    // 2. Deteção de QUEDA (Queda livre seguida de impacto)
                    // Passo A: Detetar queda livre (aceleração perto de 0)
                    if (acceleration < FALL_THRESHOLD) {
                        wasInFreeFall = true
                    }
                    // Passo B: Detetar o impacto no chão se esteve em queda livre
                    if (wasInFreeFall && acceleration > IMPACT_THRESHOLD) {
                        trySend("QUEDA")
                        wasInFreeFall = false // Reset
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            if (accelerometer != null) {
                sensorManager.registerListener(listener, accelerometer, AndroidSensorManager.SENSOR_DELAY_UI)
            }

            awaitClose {
                sensorManager.unregisterListener(listener)
            }
        }
    }
}