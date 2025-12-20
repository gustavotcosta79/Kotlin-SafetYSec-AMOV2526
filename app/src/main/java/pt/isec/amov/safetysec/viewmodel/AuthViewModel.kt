package pt.isec.amov.safetysec.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.data.model.Alert
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.data.repository.AuthRepository
import pt.isec.amov.safetysec.data.repository.FirestoreRepository

class AuthViewModel : ViewModel() {
    // Instâncias do Firebase e Repositórios
    private val auth = FirebaseAuth.getInstance()
    private val authRepository = AuthRepository()
    private val firestoreRepository = FirestoreRepository()

    // Dados do formulário
    var name by mutableStateOf("")
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var cancellationCode by mutableStateOf("")

    // Perfis
    var isMonitor by mutableStateOf(false)
    var isProtected by mutableStateOf(false)

    // Estados de controlo da UI
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    // Utilizador e Códigos de Associação
    var currentUser by mutableStateOf<User?>(null)
        private set

    var connectionCode by mutableStateOf<String?>(null) // Código que o Protegido gera
        private set

    var monitoredUsers by mutableStateOf<List<User>>(emptyList())
        private set

    var activeAlerts by mutableStateOf<List<Alert>>(emptyList())
        private set
    var codeInput by mutableStateOf("") // Código que o Monitor escreve

    init {
        // Se já existir sessão, carrega o perfil ao iniciar o ViewModel
        if (auth.currentUser != null) {
            fetchCurrentUser()
        }
    }

    fun startObservingAlerts() {
        val user = currentUser ?: return
        // Usamos os emails dos protegidos que já estão na lista monitoredUsers
        val targets = monitoredUsers.map { it.email }

        if (targets.isNotEmpty()) {
            firestoreRepository.listenForAlerts(targets) { alerts ->
                activeAlerts = alerts
            }
        }
    }

    fun fetchCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val user = authRepository.getUserProfile(uid)
            currentUser = user

            //tentar atualizar a lista logo ao carregar o user (se formos monitor claro)
            if (user != null && user.isMonitor){
                fetchMonitoredUsersDirectly(user)
            }
        }
    }



    fun fetchMonitoredUsers() {
        val user = currentUser ?: return
        fetchMonitoredUsersDirectly(user)
    }

    private fun fetchMonitoredUsersDirectly (user : User){
        if (!user.isMonitor || user.associatedProtegidoIds.isEmpty()){
            monitoredUsers = emptyList()
            return
        }

        viewModelScope.launch {
            val result = firestoreRepository.getAssociatedUsers(user.associatedProtegidoIds)
            if (result.isSuccess){
                monitoredUsers = result.getOrNull() ?: emptyList()
                startObservingAlerts()
            }
        }
    }

    // --- Lógica de Login ---
    fun onLoginClick(onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Preencha todos os campos."
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            val result = authRepository.login(email, password)
            isLoading = false
            if (result.isSuccess) {
                currentUser = result.getOrNull()
                onSuccess()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Erro ao iniciar sessão."
            }
        }
    }

    // --- Lógica de Registo ---
    fun onRegisterClick(onSuccess: () -> Unit) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            errorMessage = "Preencha todos os campos."
            return
        }
        if (!isMonitor && !isProtected) {
            errorMessage = "Selecione pelo menos um perfil."
            return
        }
        if (isProtected && cancellationCode.length < 4) {
            errorMessage = "O código de cancelamento deve ter pelo menos 4 dígitos."
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            val codeToSend = if (isProtected) cancellationCode else ""
            val result = authRepository.register(name, email, password, isMonitor, isProtected, codeToSend)

            isLoading = false
            if (result.isSuccess) {
                onSuccess()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Erro no registo."
            }
        }
    }

    // --- Lógica de Logout ---
    fun onLogoutClick(onSuccess: () -> Unit) {
        auth.signOut()
        currentUser = null
        connectionCode = null
        onSuccess()
    }

    // --- Lógica de Associação (Protegido gera o código) ---
    fun generateCode() {
        val user = currentUser ?: return
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val newCode = (1..6).map { chars.random() }.joinToString("")

        viewModelScope.launch {
            isLoading = true
            val result = firestoreRepository.updateConnectionCode(user.id, newCode)
            isLoading = false

            if (result.isSuccess) {
                connectionCode = newCode
            } else {
                errorMessage = "Erro ao gerar código."
            }
        }
    }

    // --- Lógica de Associação (Monitor submete o código) ---
    fun onLinkSubmit(onSuccess: () -> Unit) {
        val monitor = currentUser ?: return
        if (codeInput.isBlank()) {
            errorMessage = "Introduza o código gerado pelo protegido."
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            val result = firestoreRepository.linkMonitorToProtected(monitor.id, codeInput)

            if (result.isSuccess) {
                codeInput = ""
                val updatedUser = authRepository.getUserProfile(monitor.id)
                currentUser = updatedUser

                if (updatedUser != null && updatedUser.isMonitor){
                    val usersResult = firestoreRepository.getAssociatedUsers(updatedUser.associatedProtegidoIds)
                    if (usersResult.isSuccess){
                        monitoredUsers = usersResult.getOrNull() ?: emptyList()
                        startObservingAlerts()
                    }
                }
                isLoading = false
                onSuccess()

            } else {
                isLoading = false
                errorMessage = result.exceptionOrNull()?.message ?: "Erro ao associar."
            }
        }
    }
}