package pt.isec.amov.safetysec.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
import java.util.Date
import kotlin.math.log10

class ProtegidoViewModel (
    private val locationManager: LocationManager,
    private val firestoreRepository: FirestoreRepository
    ) : ViewModel()
{
    private var monitoringJob: Job? = null
    // Lista de regras ativas para este protegido (deverás carregar isto do Firestore)
    var activeRules by mutableStateOf<List<Rule>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)
    var activeAlertId by mutableStateOf<String?>(null) //se for null está tudo nice

    var isCountingDown by mutableStateOf(false)
    var countdownValue by mutableStateOf(10)
    private var countdownJob: Job? = null

    var alertHistory by mutableStateOf<List<Alert>>(emptyList())
        private set

    // --- NOVA LISTA DE REGRAS ---
    var rules by mutableStateOf<List<Rule>>(emptyList())
        private set


    fun fetchAlertHistory(userId: String) {
        viewModelScope.launch {
            val result = firestoreRepository.getAlertHistory(userId)
            if (result.isSuccess) {
                alertHistory = result.getOrNull() ?: emptyList()
            }
        }
    }

    // Inicia a escuta das regras (chamado ao entrar no ecrã)
    fun startObservingRules(userId: String) {
        firestoreRepository.listenToRules(userId) { listaAtualizada ->
            rules = listaAtualizada
        }
    }

    // Aceitar (true) ou Revogar (false) uma regra
    fun toggleRule(ruleId: String, novoEstado: Boolean) {
        Log.d("SafetySec", "Tentativa de alterar regra: ID='$ruleId' para Estado=$novoEstado")

        if (ruleId.isBlank()) {
            Log.e("SafetySec", "ERRO FATAL: O ID da regra está vazio! A regra não foi gravada corretamente.")
            return
        }

        viewModelScope.launch {
            val result = firestoreRepository.updateRuleStatus(ruleId, novoEstado)

            if (result.isSuccess) {
                Log.d("SafetySec", "SUCESSO: Estado atualizado no Firestore.")
            } else {
                val erro = result.exceptionOrNull()?.message
                Log.e("SafetySec", "ERRO ao atualizar: $erro")
            }
        }
    }

    fun startPanicProcess(user: User) {
        if (activeAlertId != null) return // Já existe um alerta real, ignora

        // Prepara a contagem
        isCountingDown = true
        countdownValue = 10
        errorMessage = null
        successMessage = null

        // Inicia a corrotina do timer
        countdownJob = viewModelScope.launch {
            while (countdownValue > 0) {
                delay(1000L) // Espera 1 segundo
                countdownValue--
            }

            // SE CHEGAR AQUI, O TEMPO ACABOU! ENVIAR ALERTA REAL!
            isCountingDown = false
            sendPanicAlert(user)
        }
    }

    /**
     * Inicia a monitorização ativa de GPS para Velocidade e Inatividade
     */
    fun startAutomaticMonitoring(user: User) {
        monitoringJob?.cancel() // Garante que não há duplicados

        monitoringJob = viewModelScope.launch {
            locationManager.getLocationUpdates(intervalMs = 10000L).collectLatest { locationData ->

                // 1. Verificar Velocidade
                checkSpeedLimit(locationData.speedKmh, user)

                // 2. Verificar Inatividade
                checkInactivity(locationData.timeStamp, user)

                // 3. Atualizar última localização conhecida no Firestore para o Monitor ver
                firestoreRepository.updateLastLocation(
                    user.id,
                    locationData.latitude,
                    locationData.longitude,
                    locationData.timeStamp
                )
            }
        }
    }


    private fun checkSpeedLimit(currentSpeed: Double, user: User) {
        val speedRule = activeRules.find { it.type == RuleType.CONTROLO_VELOCIDADE && it.isActive }
        speedRule?.valueDouble?.let { limit ->
            if (currentSpeed > limit) {
                triggerAlertProcess(RuleType.CONTROLO_VELOCIDADE, user)
            }
        }
    }

    private fun checkInactivity(lastMovementTime: Long, user: User) {
        val inactivityRule = activeRules.find { it.type == RuleType.INATIVIDADE && it.isActive }
        inactivityRule?.valueDouble?.let { maxMinutes ->
            val diffMinutes = (System.currentTimeMillis() - lastMovementTime) / 60000
            if (diffMinutes >= maxMinutes) {
                triggerAlertProcess(RuleType.INATIVIDADE, user)
            }
        }
    }

    /**
     * Esta função é a ponte para o trabalho do teu colega.
     * Ela deve iniciar o timer de 10s antes de enviar o alerta final.
     */
    private fun triggerAlertProcess(type: RuleType, user: User) {
        // Aqui o teu colega chamará a UI de "Contagem Decrescente"
        // Se após 10s não for cancelado, chama o firestoreRepository.createAlert(...)
        Log.d("SafetYSec", "ALERTA DETETADO: $type. A iniciar contagem de 10s.")
    }

    override fun onCleared() {
        super.onCleared()
        monitoringJob?.cancel()
    }


    fun sendPanicAlert (user : User){
        isLoading = true
        errorMessage = null
        successMessage = null

        viewModelScope.launch {
            var location = locationManager.getCurrentLocation()

            if (location == null){
                errorMessage = "Não foi possível obter a localização (gps desligado ou erro)."
                isLoading = false
                return@launch
            }

            val alert = Alert(
                id = "",
                type = RuleType.BOTAO_PANICO,
                userEmail = user.email,
                protectedId = user.id,
                date = Date(),
                latitude = location.latitude,
                longitude = location.longitude,
                solved = false,
                cancelled = false
            )

            val result = firestoreRepository.createAlert(alert)

            isLoading = false
            if (result.isSuccess) {
                activeAlertId = result.getOrNull()
                successMessage = "ALERTA ENVIADO! O monitor foi notificado."
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Erro ao enviar alerta."
            }
        }
    }

    fun handleCancelRequest(inputPin: String, correctPin: String) {
        if (inputPin != correctPin) {
            errorMessage = "PIN incorreto!"
            return
        }

        // CASO A: Estamos na contagem decrescente?
        if (isCountingDown) {
            countdownJob?.cancel() // Pára o relógio
            isCountingDown = false
            countdownValue = 10
            successMessage = "Envio abortado. Nada foi enviado."
            return
        }

        // CASO B: O alerta já foi enviado?
        if (activeAlertId != null) {
            cancelPanicAlert(activeAlertId!!)
        }
    }
    fun cancelPanicAlert (alertId: String){
        isLoading = true
        viewModelScope.launch {
            val result = firestoreRepository.cancelAlert(alertId)
            isLoading = false
            if (result.isSuccess) {
                activeAlertId = null
                successMessage = "Alerta na base de dados cancelado."
            } else {
                errorMessage = "Erro ao cancelar na BD."
            }
        }    }
}


class ProtegidoViewModelFactory(
    private val locationManager: LocationManager,
    private val firestoreRepository: FirestoreRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProtegidoViewModel(locationManager, firestoreRepository) as T
    }
}