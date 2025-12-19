package pt.isec.amov.safetysec.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
                date = Date(),
                latitude = location.latitude,
                longitude = location.longitude,
                solved = false,
                cancelled = false
            )

            val result = firestoreRepository.createAlert(alert)

            isLoading = false
            if (result.isSuccess) {
                successMessage = "ALERTA ENVIADO! O monitor foi notificado."
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Erro ao enviar alerta."
            }
        }
    }
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