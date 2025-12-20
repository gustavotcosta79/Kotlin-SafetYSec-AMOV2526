package pt.isec.amov.safetysec.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.data.model.Alert
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
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)
    var activeAlertId by mutableStateOf<String?>(null) //se for null está tudo nice

    var isCountingDown by mutableStateOf(false)
    var countdownValue by mutableStateOf(10)
    private var countdownJob: Job? = null

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