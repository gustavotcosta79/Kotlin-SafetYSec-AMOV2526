package pt.isec.amov.safetysec.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.data.repository.AuthRepository

class AuthViewModel : ViewModel() {
    // Dados do formulário
    var name by mutableStateOf("")
    var email by mutableStateOf("")
    var password by mutableStateOf("")

    // Perfis (Requisito I-16: Podem ser ambos verdadeiros)
    var isMonitor by mutableStateOf(false)
    var isProtected by mutableStateOf(false)

    // Estados de controlo da UI
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private val repository = AuthRepository()

    // --- Lógica de Login ---
    fun onLoginClick(onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Preencha todos os campos."
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            val result = repository.login(email, password)
            isLoading = false
            if (result.isSuccess) {
                onSuccess()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Erro ao iniciar sessão."
            }
        }
    }

    // --- Lógica de Registo (Adaptada ao teu modelo) ---
    fun onRegisterClick(onSuccess: () -> Unit) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            errorMessage = "Preencha todos os campos."
            return
        }

        // Validação: Tem de escolher pelo menos um perfil
        if (!isMonitor && !isProtected) {
            errorMessage = "Selecione pelo menos um perfil (Monitor ou Protegido)."
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            // Chama o repositório passando os dois booleanos dos perfis
            val result = repository.register(name, email, password, isMonitor, isProtected)

            isLoading = false
            if (result.isSuccess) {
                onSuccess()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Erro ao criar conta."
            }
        }
    }

    // --- Lógica de Logout ---
    fun onLogoutClick(onSuccess: () -> Unit) {
        repository.logout()
        onSuccess()
    }
}