package pt.isec.amov.safetysec.viewmodel

import android.app.Application // Importante
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel // Importante
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.R // Importante
import pt.isec.amov.safetysec.data.model.Alert
import pt.isec.amov.safetysec.data.model.Rule
import pt.isec.amov.safetysec.data.model.RuleType
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.data.repository.FirestoreRepository

// Passamos a usar AndroidViewModel para ter acesso ao Contexto/Strings
class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FirestoreRepository = FirestoreRepository()

    // Helper para obter strings traduzidas
    private fun getString(resId: Int): String {
        return getApplication<Application>().getString(resId)
    }

    var isLoading by mutableStateOf(false)

    // Dados para o formulário de criação de regra
    var selectedRuleType by mutableStateOf(RuleType.CONTROLO_VELOCIDADE)
    var ruleValueInput by mutableStateOf("")
    var ruleDescription by mutableStateOf("")

    var currentRules by mutableStateOf<List<Rule>>(emptyList())
        private set

    var monitorAlertHistory by mutableStateOf<List<Alert>>(emptyList())

    fun fetchMonitorAlertHistory(monitoredUsers: List<User>) {
        val ids = monitoredUsers.map { it.id }

        if (ids.isEmpty()) {
            monitorAlertHistory = emptyList()
            return
        }

        isLoading = true
        viewModelScope.launch {
            val result = repository.getAlertsForMonitor(ids)
            if (result.isSuccess) {
                monitorAlertHistory = result.getOrNull() ?: emptyList()
            }
            isLoading = false
        }
    }

    fun fetchRulesForProtected(protectedId: String) {
        isLoading = true
        viewModelScope.launch {
            val result = repository.getRulesForUser(protectedId)
            if (result.isSuccess) {
                currentRules = result.getOrNull() ?: emptyList()
            }
            isLoading = false
        }
    }

    // Apagar regra
    fun deleteRule(ruleId: String, protectedId: String) {
        viewModelScope.launch {
            repository.deleteRule(ruleId)
            fetchRulesForProtected(protectedId)
        }
    }

    // Editar regra
    fun updateRule(ruleId: String, newValue: String, newDesc: String, protectedId: String, onFinished: () -> Unit) {
        val valueDouble = newValue.toDoubleOrNull()
        val updates = mapOf(
            "valueDouble" to (valueDouble ?: 0.0),
            "description" to newDesc
        )

        viewModelScope.launch {
            repository.updateRule(ruleId, updates)
            fetchRulesForProtected(protectedId)
            onFinished()
        }
    }

    fun submitRule(monitorId: String, targetUser: User, onFinished: () -> Unit) {
        isLoading = true

        val valueInputDouble = ruleValueInput.toDoubleOrNull()

        // Lógica específica para Geofencing
        var finalLat: Double? = null
        var finalLon: Double? = null
        var finalRadius: Double? = null
        var finalValue: Double? = null

        if (selectedRuleType == RuleType.GEOFENCING) {
            // Se for Geofencing, o valor inserido é o RAIO
            finalRadius = valueInputDouble

            // Centro é a última localização do protegido
            finalLat = targetUser.lastLatitude
            finalLon = targetUser.lastLongitude
        } else {
            // Para Velocidade e Inatividade
            finalValue = valueInputDouble
        }

        // --- INTERNACIONALIZAÇÃO DA DESCRIÇÃO AUTOMÁTICA ---
        val finalDescription = if (ruleDescription.isNotBlank()) {
            ruleDescription
        } else {
            // Se o utilizador não escreveu nada, geramos um nome traduzido
            when (selectedRuleType) {
                RuleType.CONTROLO_VELOCIDADE -> getString(R.string.default_desc_speed)
                RuleType.INATIVIDADE -> getString(R.string.default_desc_inactivity)
                RuleType.GEOFENCING -> getString(R.string.default_desc_geo)
                RuleType.QUEDA -> getString(R.string.default_desc_fall)
                RuleType.ACIDENTE -> getString(R.string.default_desc_accident)
                RuleType.BOTAO_PANICO -> getString(R.string.default_desc_panic)
                else -> getString(R.string.default_desc_unknown)
            }
        }
        // ----------------------------------------------------

        val newRule = Rule(
            type = selectedRuleType,
            description = finalDescription,
            isActive = false,
            monitorId = monitorId,
            protectedId = targetUser.id,
            valueDouble = finalValue,
            radius = finalRadius,
            latitude = finalLat,
            longitude = finalLon
        )

        viewModelScope.launch {
            val result = repository.proposeRule(newRule)

            isLoading = false
            if (result.isSuccess) {
                ruleValueInput = ""
                ruleDescription = ""
                onFinished()
            }
        }
    }
}