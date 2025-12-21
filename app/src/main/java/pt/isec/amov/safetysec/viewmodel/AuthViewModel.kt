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
    var successMessage by mutableStateOf<String?>(null)

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

    var editName by mutableStateOf("")
    var editCancellationCode by mutableStateOf("")
    var editPassword by mutableStateOf("")
    var editConfirmPassword by mutableStateOf("")

    var associatedMonitors by mutableStateOf<List<User>>(emptyList())
        private set

    init {
        // Se já existir sessão, carrega o perfil ao iniciar o ViewModel
        if (auth.currentUser != null) {
            fetchCurrentUser()
        }
    }

    fun initializeEditState() {
        currentUser?.let { user ->
            editName = user.name
            editCancellationCode = user.cancellationCode
            editPassword = ""
            editConfirmPassword = ""
            errorMessage = null
            successMessage = null // (Adiciona esta var no topo do VM se não tiveres)
        }
    }

    var profileUpdateSuccess by mutableStateOf(false)
    fun onSaveChangesClick() {
        val user = currentUser ?: return
        errorMessage = null
        profileUpdateSuccess = false
        isLoading = true

        viewModelScope.launch {
            val updates = mutableMapOf<String, Any>()

            // 1. Verificar alterações de Nome
            if (editName.isNotBlank() && editName != user.name) {
                updates["name"] = editName
            }

            // 2. Verificar alterações de Código (Só Protegido)
            if (user.isProtected) {
                if (editCancellationCode.length < 4) {
                    errorMessage = "O código deve ter pelo menos 4 dígitos."
                    isLoading = false
                    return@launch
                }
                if (editCancellationCode != user.cancellationCode) {
                    updates["cancellationCode"] = editCancellationCode
                }
            }

            // 3. Atualizar na BD
            if (updates.isNotEmpty()) {
                val result = firestoreRepository.updateUserProfile(user.id, updates)
                if (result.isFailure) {
                    errorMessage = "Erro ao atualizar dados: ${result.exceptionOrNull()?.message}"
                    isLoading = false
                    return@launch
                }
            }

            // 4. Atualizar Password (se preenchida)
            if (editPassword.isNotBlank()) {
                if (editPassword != editConfirmPassword) {
                    errorMessage = "As passwords não coincidem."
                    isLoading = false
                    return@launch
                }
                if (editPassword.length < 6) {
                    errorMessage = "A password deve ter pelo menos 6 caracteres."
                    isLoading = false
                    return@launch
                }

                val passResult = authRepository.updatePassword(editPassword)
                if (passResult.isFailure) {
                    errorMessage = "Erro ao atualizar password. Talvez precise de fazer login novamente."
                    isLoading = false
                    return@launch
                }
            }

            // Sucesso Total
            fetchCurrentUser() // Atualiza os dados locais
            isLoading = false
            profileUpdateSuccess = true
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

    fun disassociateProtected (protectedId : String){
        val monitor = currentUser ?: return
        isLoading = true

        viewModelScope.launch {
            val result = firestoreRepository.removeAssociation(monitor.id,protectedId)

            if (result.isSuccess){
                // Atualizar a lista localmente removendo o user apagado (para ser instantâneo)
                monitoredUsers = monitoredUsers.filter { it.id != protectedId }

                startObservingAlerts()

                val updateUser = authRepository.getUserProfile(monitor.id)
                currentUser = updateUser
            }else {
                errorMessage = "Erro ao desassociar o protegido."
            }
            isLoading = false
        }
    }

    // Buscar a lista de monitores
    fun fetchAssociatedMonitors() {
        val user = currentUser ?: return
        // Se não for protegido ou não tiver monitores, limpa a lista
        if (!user.isProtected || user.associatedMonitorIds.isEmpty()) {
            associatedMonitors = emptyList()
            return
        }

        viewModelScope.launch {
            // Reutilizamos a função getAssociatedUsers, pois ela serve para qualquer lista de IDs
            val result = firestoreRepository.getAssociatedUsers(user.associatedMonitorIds)
            if (result.isSuccess) {
                associatedMonitors = result.getOrNull() ?: emptyList()
            }
        }
    }

    // Desassociar um Monitor (Ação feita pelo Protegido)
    fun disassociateMonitor(monitorId: String) {
        val protectedUser = currentUser ?: return
        isLoading = true

        viewModelScope.launch {
            // A função removeAssociation pede (monitorId, protectedId).
            // Aqui somos o protegido, por isso passamos o ID do monitor alvo e o nosso ID.
            val result = firestoreRepository.removeAssociation(monitorId, protectedUser.id)

            if (result.isSuccess) {
                // Atualiza a lista visualmente
                associatedMonitors = associatedMonitors.filter { it.id != monitorId }

                val updatedUser = authRepository.getUserProfile(protectedUser.id)
                currentUser = updatedUser
            } else {
                errorMessage = "Erro ao desassociar o monitor"
            }
            isLoading = false
        }
    }
}