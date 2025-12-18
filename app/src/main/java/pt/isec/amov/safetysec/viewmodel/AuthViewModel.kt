package pt.isec.amov.safetysec.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.data.repository.AuthRepository

class AuthViewModel : ViewModel(){
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private val repository = AuthRepository()

    fun onLoginClick (onSuccess: ()->Unit){
        if (email.isBlank() || password.isBlank()){
            errorMessage = "Preencha todos os campos."
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            val result = repository.login(email,password)

            isLoading = false
            if (result.IsSuccess){
                onSuccess()
            }
            else {
                errorMessage = result.exceptionOrNull()?.message?: "Erro ao iniciar sessão."
            }
        }
    }
}