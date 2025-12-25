package pt.isec.amov.safetysec.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.data.model.Alert
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.data.repository.AuthRepository
import pt.isec.amov.safetysec.data.repository.FirestoreRepository

// Mudámos de ViewModel() para AndroidViewModel(application) para ter acesso às strings
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    // Instâncias do Firebase e Repositórios
    private val auth = FirebaseAuth.getInstance()
    private val authRepository = AuthRepository()
    private val firestoreRepository = FirestoreRepository()

    // Helper para obter strings traduzidas
    private fun getString(resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

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

    var connectionCode by mutableStateOf<String?>(null)
        private set

    var monitoredUsers by mutableStateOf<List<User>>(emptyList())
        private set

    var activeAlerts by mutableStateOf<List<Alert>>(emptyList())
        private set

    var codeInput by mutableStateOf("")

    var editName by mutableStateOf("")
    var editCancellationCode by mutableStateOf("")
    var editPassword by mutableStateOf("")
    var editConfirmPassword by mutableStateOf("")

    var associatedMonitors by mutableStateOf<List<User>>(emptyList())
        private set

    init {
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
            successMessage = null
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

            if (editName.isNotBlank() && editName != user.name) {
                updates["name"] = editName
            }

            if (user.isProtected) {
                if (editCancellationCode.length < 4) {
                    errorMessage = getString(R.string.error_pin_length)
                    isLoading = false
                    return@launch
                }
                if (editCancellationCode != user.cancellationCode) {
                    updates["cancellationCode"] = editCancellationCode
                }
            }

            if (updates.isNotEmpty()) {
                val result = firestoreRepository.updateUserProfile(user.id, updates)
                if (result.isFailure) {
                    errorMessage = getString(R.string.error_update_data, result.exceptionOrNull()?.message ?: "")
                    isLoading = false
                    return@launch
                }
            }

            if (editPassword.isNotBlank()) {
                if (editPassword != editConfirmPassword) {
                    errorMessage = getString(R.string.error_passwords_mismatch)
                    isLoading = false
                    return@launch
                }
                if (editPassword.length < 6) {
                    errorMessage = getString(R.string.error_password_length)
                    isLoading = false
                    return@launch
                }

                val passResult = authRepository.updatePassword(editPassword)
                if (passResult.isFailure) {
                    errorMessage = getString(R.string.error_update_password)
                    isLoading = false
                    return@launch
                }
            }

            fetchCurrentUser()
            isLoading = false
            profileUpdateSuccess = true
        }
    }

    fun startObservingAlerts() {
        val targets = monitoredUsers.map { it.id }
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
            errorMessage = getString(R.string.error_fill_all_fields)
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
                errorMessage = result.exceptionOrNull()?.message ?: getString(R.string.error_login_generic)
            }
        }
    }

    // --- Lógica de Registo ---
    fun onRegisterClick(onSuccess: () -> Unit) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            errorMessage = getString(R.string.error_fill_all_fields)
            return
        }
        if (!isMonitor && !isProtected) {
            errorMessage = getString(R.string.error_select_profile)
            return
        }
        if (isProtected && cancellationCode.length < 4) {
            errorMessage = getString(R.string.error_cancel_code_length)
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
                errorMessage = result.exceptionOrNull()?.message ?: getString(R.string.error_register_generic)
            }
        }
    }

    fun onLogoutClick(onSuccess: () -> Unit) {
        auth.signOut()
        currentUser = null
        connectionCode = null
        onSuccess()
    }

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
                errorMessage = getString(R.string.error_generate_code)
            }
        }
    }

    fun onLinkSubmit(onSuccess: () -> Unit) {
        val monitor = currentUser ?: return
        if (codeInput.isBlank()) {
            errorMessage = getString(R.string.error_enter_otp)
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
                errorMessage = result.exceptionOrNull()?.message ?: getString(R.string.error_linking)
            }
        }
    }

    fun disassociateProtected (protectedId : String){
        val monitor = currentUser ?: return
        isLoading = true

        viewModelScope.launch {
            val result = firestoreRepository.removeAssociation(monitor.id,protectedId)

            if (result.isSuccess){
                monitoredUsers = monitoredUsers.filter { it.id != protectedId }
                startObservingAlerts()
                val updateUser = authRepository.getUserProfile(monitor.id)
                currentUser = updateUser
            }else {
                errorMessage = getString(R.string.error_disassociate_protected)
            }
            isLoading = false
        }
    }

    fun fetchAssociatedMonitors() {
        val user = currentUser ?: return
        if (!user.isProtected || user.associatedMonitorIds.isEmpty()) {
            associatedMonitors = emptyList()
            return
        }

        viewModelScope.launch {
            val result = firestoreRepository.getAssociatedUsers(user.associatedMonitorIds)
            if (result.isSuccess) {
                associatedMonitors = result.getOrNull() ?: emptyList()
            }
        }
    }

    fun disassociateMonitor(monitorId: String) {
        val protectedUser = currentUser ?: return
        isLoading = true

        viewModelScope.launch {
            val result = firestoreRepository.removeAssociation(monitorId, protectedUser.id)

            if (result.isSuccess) {
                associatedMonitors = associatedMonitors.filter { it.id != monitorId }
                val updatedUser = authRepository.getUserProfile(protectedUser.id)
                currentUser = updatedUser
            } else {
                errorMessage = getString(R.string.error_disassociate_monitor)
            }
            isLoading = false
        }
    }
}