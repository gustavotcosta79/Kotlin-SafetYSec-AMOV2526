package pt.isec.amov.safetysec.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.data.model.Alert
import pt.isec.amov.safetysec.data.model.Rule
import pt.isec.amov.safetysec.data.model.RuleType
import pt.isec.amov.safetysec.data.model.TimeWindow
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.data.repository.FirestoreRepository
import pt.isec.amov.safetysec.managers.LocationManager
import pt.isec.amov.safetysec.managers.SensorManager
import java.util.Date

class ProtegidoViewModel(
    application: Application,
    private val locationManager: LocationManager,
    private val sensorManager: SensorManager,
    private val firestoreRepository: FirestoreRepository
) : AndroidViewModel(application) {

    // Helper para obter strings
    private fun getString(resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    // --- ESTADOS DE CARREGAMENTO E MENSAGENS ---
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)

    // --- DADOS ---
    var rules by mutableStateOf<List<Rule>>(emptyList())
        private set

    var alertHistory by mutableStateOf<List<Alert>>(emptyList())
        private set

    // --- ESTADOS DE ALERTA ---
    var activeAlertId by mutableStateOf<String?>(null)
    var isCountingDown by mutableStateOf(false)
    var countdownValue by mutableIntStateOf(10)
    var currentAlertType by mutableStateOf(RuleType.UNKNOWN)

    // --- CONTROLO INTERNO ---
    private var monitoringJob: Job? = null
    private var countdownJob: Job? = null
    private var sensorJob: Job? = null

    private var lastMovementTime: Long = System.currentTimeMillis()
    private var lastTriggerTime: Long = 0

    var timeWindows = mutableStateListOf<TimeWindow>()
        private set

    // =========================================================================
    // INICIALIZAÇÃO E LISTAGENS
    // =========================================================================

    fun startObservingRules(userId: String) {
        firestoreRepository.listenToRules(userId) { listaAtualizada ->
            rules = listaAtualizada
        }
    }

    fun fetchAlertHistory(userId: String) {
        viewModelScope.launch {
            val result = firestoreRepository.getAlertHistory(userId)
            if (result.isSuccess) {
                alertHistory = result.getOrNull() ?: emptyList()
            }
        }
    }

    fun toggleRule(ruleId: String, novoEstado: Boolean) {
        if (ruleId.isBlank()) return
        viewModelScope.launch {
            firestoreRepository.updateRuleStatus(ruleId, novoEstado)
        }
    }

    fun startTimeWindowObservation(userId: String) {
        firestoreRepository.listenToTimeWindows(userId) { windows ->
            timeWindows.clear()
            timeWindows.addAll(windows)
        }
    }

    fun addTimeWindow(userId: String, window: pt.isec.amov.safetysec.data.model.TimeWindow) {
        viewModelScope.launch { firestoreRepository.addTimeWindow(userId, window) }
    }

    fun deleteTimeWindow(userId: String, windowId: String) {
        viewModelScope.launch { firestoreRepository.deleteTimeWindow(userId, windowId) }
    }

    private fun isMonitoringAllowed(): Boolean {
        if (timeWindows.isEmpty()) return true
        return timeWindows.any { it.isActiveNow() }
    }

    // =========================================================================
    // MOTOR DE MONITORIZAÇÃO AUTOMÁTICA
    // =========================================================================

    fun startAutomaticMonitoring(user: User) {
        if (monitoringJob?.isActive == true) return

        monitoringJob = viewModelScope.launch {
            locationManager.getLocationUpdates(intervalMs = 2000L).collect { locationData ->

                if (!isMonitoringAllowed()) {
                    lastMovementTime = System.currentTimeMillis()
                    return@collect
                }

                if (locationData.speedKmh > 2.0) {
                    lastMovementTime = System.currentTimeMillis()
                }

                if (!isCountingDown && activeAlertId == null) {
                    checkSpeedLimit(locationData.speedKmh, user)
                    checkInactivity(user)
                    checkGeofencing(locationData.latitude, locationData.longitude, user)
                }

                firestoreRepository.updateLastLocation(
                    user.id,
                    locationData.latitude,
                    locationData.longitude,
                    locationData.timeStamp
                )
            }
        }
    }

    fun startSensorMonitoring(user: User) {
        if (sensorJob?.isActive == true) return

        sensorJob = viewModelScope.launch {
            sensorManager.getAccelerationEvents().collect { eventType ->
                if (activeAlertId != null || isCountingDown || !isMonitoringAllowed()) return@collect

                when (eventType) {
                    "QUEDA" -> {
                        val ruleQueda = rules.find { it.type == RuleType.QUEDA && it.isActive }
                        if (ruleQueda != null) triggerAlertProcess(RuleType.QUEDA, user)
                    }
                    "ACIDENTE" -> {
                        val ruleAcidente = rules.find { it.type == RuleType.ACIDENTE && it.isActive }
                        if (ruleAcidente != null) triggerAlertProcess(RuleType.ACIDENTE, user)
                    }
                }
            }
        }
    }

    private fun checkSpeedLimit(currentSpeed: Double, user: User) {
        val speedRule = rules.find { it.type == RuleType.CONTROLO_VELOCIDADE && it.isActive }
        speedRule?.valueDouble?.let { limit ->
            if (currentSpeed > limit) triggerAlertProcess(RuleType.CONTROLO_VELOCIDADE, user)
        }
    }

    private fun checkInactivity(user: User) {
        val inactivityRule = rules.find { it.type == RuleType.INATIVIDADE && it.isActive }
        inactivityRule?.valueDouble?.let { maxMinutes ->
            val diffMillis = System.currentTimeMillis() - lastMovementTime
            val diffMinutes = diffMillis / 60000.0
            if (diffMinutes >= maxMinutes) triggerAlertProcess(RuleType.INATIVIDADE, user)
        }
    }

    private fun checkGeofencing(lat: Double, lon: Double, user: User) {
        val geoRules = rules.filter { it.type == RuleType.GEOFENCING && it.isActive }
        for (rule in geoRules) {
            if (rule.latitude != null && rule.longitude != null && rule.radius != null) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(lat, lon, rule.latitude, rule.longitude, results)
                val distanceInMeters = results[0]
                if (distanceInMeters > rule.radius) {
                    triggerAlertProcess(RuleType.GEOFENCING, user)
                    break
                }
            }
        }
    }

    // =========================================================================
    // GESTÃO DE ALERTAS
    // =========================================================================

    fun startPanicProcess(user: User) {
        triggerAlertProcess(RuleType.BOTAO_PANICO, user)
    }

    private fun triggerAlertProcess(type: RuleType, user: User) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 30_000 && type != RuleType.BOTAO_PANICO) return
        if (activeAlertId != null || isCountingDown) return

        lastTriggerTime = now
        currentAlertType = type
        isCountingDown = true
        countdownValue = 10
        errorMessage = null
        successMessage = null

        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (countdownValue > 0) {
                delay(1000L)
                countdownValue--
            }
            isCountingDown = false
            sendAlert(user, type)
        }
    }

    private fun sendAlert(user: User, type: RuleType) {
        isLoading = true
        viewModelScope.launch {
            val location = locationManager.getCurrentLocation()
            val lat = location?.latitude ?: user.lastLatitude ?: 0.0
            val lon = location?.longitude ?: user.lastLongitude ?: 0.0

            val alert = Alert(
                id = "",
                type = type,
                userEmail = user.email,
                protectedId = user.id,
                date = Date(),
                latitude = lat,
                longitude = lon,
                solved = false,
                cancelled = false
            )

            val result = firestoreRepository.createAlert(alert)
            isLoading = false
            if (result.isSuccess) {
                activeAlertId = result.getOrNull()
                successMessage = getString(R.string.alert_sent_success)
            } else {
                errorMessage = getString(R.string.alert_sent_error, result.exceptionOrNull()?.message ?: "")
                activeAlertId = null
            }
        }
    }

    // =========================================================================
    // CANCELAMENTO E VIDEO
    // =========================================================================

    // MUDANÇA: Agora recebe userId para cancelar TODOS os alertas ativos
    fun handleCancelRequest(inputPin: String, correctPin: String, userId: String) {
        if (inputPin != correctPin) {
            errorMessage = getString(R.string.pin_incorrect)
            return
        }

        if (isCountingDown) {
            countdownJob?.cancel()
            isCountingDown = false
            countdownValue = 10
            successMessage = getString(R.string.alert_cancelled_time)
            errorMessage = null
            activeAlertId = null
            return
        }

        // Se já está na base de dados, cancelamos TODOS do utilizador
        cancelAllAlertsForUser(userId)
    }

    private fun cancelAllAlertsForUser(userId: String) {
        isLoading = true
        viewModelScope.launch {
            // Chama a nova função do repositório
            val result = firestoreRepository.cancelAllActiveAlerts(userId)

            isLoading = false
            if (result.isSuccess) {
                activeAlertId = null
                successMessage = getString(R.string.alert_cancelled_success)
            } else {
                errorMessage = getString(R.string.alert_cancel_db_error)
            }
        }
    }

    fun uploadAndLinkVideo(
        file: java.io.File,
        cameraManager: pt.isec.amov.safetysec.managers.CameraManager,
        onComplete: () -> Unit
    ) {
        val alertId = activeAlertId ?: return
        viewModelScope.launch {
            val downloadUrl = cameraManager.uploadVideo(file)
            if (downloadUrl != null) {
                firestoreRepository.updateAlertVideo(alertId, downloadUrl)
                successMessage = getString(R.string.video_sent_success)
            } else {
                errorMessage = getString(R.string.video_sent_error)
            }
            try { file.delete() } catch(e: Exception){}
            onComplete()
        }
    }

    override fun onCleared() {
        super.onCleared()
        monitoringJob?.cancel()
        countdownJob?.cancel()
        sensorJob?.cancel()
    }

    class ProtegidoViewModelFactory(
        private val application: Application,
        private val locationManager: LocationManager,
        private val sensorManager: SensorManager,
        private val firestoreRepository: FirestoreRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProtegidoViewModel(application, locationManager, sensorManager, firestoreRepository) as T
        }
    }

}