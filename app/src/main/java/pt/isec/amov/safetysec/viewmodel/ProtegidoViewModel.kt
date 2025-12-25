package pt.isec.amov.safetysec.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.data.model.Alert
import pt.isec.amov.safetysec.data.model.Rule
import pt.isec.amov.safetysec.data.model.RuleType
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.data.repository.FirestoreRepository
import pt.isec.amov.safetysec.managers.LocationManager
import pt.isec.amov.safetysec.managers.SensorManager
import java.util.Date

class ProtegidoViewModel (
    private val locationManager: LocationManager,
    private val sensorManager: SensorManager,
    private val firestoreRepository: FirestoreRepository
) : ViewModel()
{
    // --- ESTADOS DE CARREGAMENTO E MENSAGENS ---
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)

    // --- DADOS ---
    // A lista de regras que vem do Firestore (atualizada em tempo real)
    var rules by mutableStateOf<List<Rule>>(emptyList())
        private set

    var alertHistory by mutableStateOf<List<Alert>>(emptyList())
        private set

    // --- ESTADOS DE ALERTA ---
    var activeAlertId by mutableStateOf<String?>(null) // ID do alerta se já foi enviado para a BD
    var isCountingDown by mutableStateOf(false)        // Se estamos nos 10s de carência
    var countdownValue by mutableIntStateOf(10)
    var currentAlertType by mutableStateOf(RuleType.UNKNOWN)

    // --- CONTROLO INTERNO ---
    private var monitoringJob: Job? = null
    private var countdownJob: Job? = null
    private var sensorJob: Job? = null // <--- Job para os sensores

    // Variável para lógica de inatividade: guarda o momento da última vez que o user se mexeu
    private var lastMovementTime: Long = System.currentTimeMillis()
    // Variável para evitar spam de alertas (ex: só alerta a cada 30s se a regra continuar quebrada)
    private var lastTriggerTime: Long = 0


    // =========================================================================
    // 1. INICIALIZAÇÃO E LISTAGENS
    // =========================================================================

    // Inicia a escuta das regras (Chamar no LaunchedEffect do Dashboard)
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

    // Aceitar (true) ou Revogar (false) uma regra
    fun toggleRule(ruleId: String, novoEstado: Boolean) {
        if (ruleId.isBlank()) return

        viewModelScope.launch {
            firestoreRepository.updateRuleStatus(ruleId, novoEstado)
        }
    }

    // =========================================================================
    // 2. MOTOR DE MONITORIZAÇÃO AUTOMÁTICA (GPS + SENSORES)
    // =========================================================================

    /**
     * Inicia a monitorização ativa de GPS (Velocidade, Inatividade, Geofencing).
     */
    fun startAutomaticMonitoring(user: User) {
        // Se já estiver a correr, não duplica
        if (monitoringJob?.isActive == true) return

        monitoringJob = viewModelScope.launch {
            // Obtemos atualizações a cada 2 segundos
            locationManager.getLocationUpdates(intervalMs = 2000L).collectLatest { locationData ->

                // A. Atualizar lógica de movimento (para a regra de inatividade)
                if (locationData.speedKmh > 2.0) {
                    // Consideramos "movimento" se for maior que 2km/h
                    lastMovementTime = System.currentTimeMillis()
                }

                // B. Verificar Regras de GPS (Apenas se não estivermos já num processo de alerta)
                if (!isCountingDown && activeAlertId == null) {
                    checkSpeedLimit(locationData.speedKmh, user)
                    checkInactivity(user)
                    checkGeofencing(locationData.latitude, locationData.longitude, user)
                }

                // C. Atualizar última localização conhecida no Firestore
                firestoreRepository.updateLastLocation(
                    user.id,
                    locationData.latitude,
                    locationData.longitude,
                    locationData.timeStamp
                )
            }
        }
    }

    /**
     * Inicia a monitorização de SENSORES (Queda e Acidente).
     * Deve ser chamado no ProtectedDashboard junto com o startAutomaticMonitoring.
     */
    fun startSensorMonitoring(user: User) {
        if (sensorJob?.isActive == true) return

        sensorJob = viewModelScope.launch {
            sensorManager.getAccelerationEvents().collect { eventType ->
                // Se já estivermos num alerta, ignorar
                if (activeAlertId != null || isCountingDown) return@collect

                when (eventType) {
                    "QUEDA" -> {
                        val ruleQueda = rules.find { it.type == RuleType.QUEDA && it.isActive }
                        if (ruleQueda != null) {
                            triggerAlertProcess(RuleType.QUEDA, user)
                        }
                    }
                    "ACIDENTE" -> {
                        val ruleAcidente = rules.find { it.type == RuleType.ACIDENTE && it.isActive }
                        if (ruleAcidente != null) {
                            triggerAlertProcess(RuleType.ACIDENTE, user)
                        }
                    }
                }
            }
        }
    }

    private fun checkSpeedLimit(currentSpeed: Double, user: User) {
        val speedRule = rules.find { it.type == RuleType.CONTROLO_VELOCIDADE && it.isActive }
        speedRule?.valueDouble?.let { limit ->
            if (currentSpeed > limit) {
                triggerAlertProcess(RuleType.CONTROLO_VELOCIDADE, user)
            }
        }
    }

    private fun checkInactivity(user: User) {
        val inactivityRule = rules.find { it.type == RuleType.INATIVIDADE && it.isActive }
        inactivityRule?.valueDouble?.let { maxMinutes ->
            val diffMillis = System.currentTimeMillis() - lastMovementTime
            val diffMinutes = diffMillis / 60000.0

            if (diffMinutes >= maxMinutes) {
                triggerAlertProcess(RuleType.INATIVIDADE, user)
            }
        }
    }

    private fun checkGeofencing(lat: Double, lon: Double, user: User) {
        val geoRules = rules.filter { it.type == RuleType.GEOFENCING && it.isActive }

        for (rule in geoRules) {
            if (rule.latitude != null && rule.longitude != null && rule.radius != null) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(lat, lon, rule.latitude, rule.longitude, results)
                val distanceInMeters = results[0]

                // --- LOG DE DEBUG PARA GEOFENCING ---
                android.util.Log.d("SafetySec_Geo", "Distância: ${distanceInMeters.toInt()}m | Raio: ${rule.radius}m")

                if (distanceInMeters > rule.radius) {
                    android.util.Log.d("SafetySec_Geo", "ALERTA DISPARADO!")
                    triggerAlertProcess(RuleType.GEOFENCING, user)
                    break
                }
            }
        }
    }

    // =========================================================================
    // 3. GESTÃO DE ALERTAS E PÂNICO
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
            // Tempo acabou: Enviar para o Firestore!
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
                successMessage = "ALERTA ENVIADO! Os monitores foram notificados."
            } else {
                errorMessage = "Erro ao enviar alerta: ${result.exceptionOrNull()?.message}"
                activeAlertId = null
            }
        }
    }

    // =========================================================================
    // 4. CANCELAMENTO (PIN)
    // =========================================================================

    fun handleCancelRequest(inputPin: String, correctPin: String) {
        if (inputPin != correctPin) {
            errorMessage = "PIN incorreto!"
            return
        }

        if (isCountingDown) {
            countdownJob?.cancel()
            isCountingDown = false
            countdownValue = 10
            successMessage = "Alerta cancelado a tempo."
            errorMessage = null
            return
        }

        if (activeAlertId != null) {
            cancelPanicAlert(activeAlertId!!)
        }
    }

    private fun cancelPanicAlert(alertId: String) {
        isLoading = true
        viewModelScope.launch {
            val result = firestoreRepository.cancelAlert(alertId)
            isLoading = false

            if (result.isSuccess) {
                activeAlertId = null
                successMessage = "Alerta cancelado. O monitor foi avisado."
            } else {
                errorMessage = "Não foi possível cancelar o alerta na base de dados."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        monitoringJob?.cancel()
        countdownJob?.cancel()
        sensorJob?.cancel() // Cancelar também os sensores
    }

    class ProtegidoViewModelFactory(
        private val locationManager: LocationManager,
        private val sensorManager: SensorManager,
        private val firestoreRepository: FirestoreRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProtegidoViewModel(locationManager, sensorManager, firestoreRepository) as T
        }
    }


    // Função para fazer upload e atualizar o alerta
    fun uploadAndLinkVideo(
        file: java.io.File,
        cameraManager: pt.isec.amov.safetysec.managers.CameraManager,
        onComplete: () -> Unit
    ) {
        val alertId = activeAlertId ?: return // Só fazemos se houver alerta

        viewModelScope.launch {
            // 1. Faz upload usando o manager
            val downloadUrl = cameraManager.uploadVideo(file)

            // 2. Se correu bem, atualiza o documento do alerta no Firestore
            if (downloadUrl != null) {
                firestoreRepository.updateAlertVideo(alertId, downloadUrl)
                successMessage = "Vídeo enviado com sucesso!"
            } else {
                errorMessage = "Falha ao enviar vídeo (verifique a internet)."
            }

            // 3. Limpa o ficheiro local para não ocupar espaço
            try { file.delete() } catch(e: Exception){}

            onComplete()
        }
    }
}