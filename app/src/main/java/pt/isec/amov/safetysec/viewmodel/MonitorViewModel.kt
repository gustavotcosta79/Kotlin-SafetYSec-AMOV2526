package pt.isec.amov.safetysec.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.data.model.Alert
import pt.isec.amov.safetysec.data.model.Rule
import pt.isec.amov.safetysec.data.model.RuleType
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.data.repository.FirestoreRepository

class MonitorViewModel(
    private val repository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    var isLoading by mutableStateOf(false)

    // Dados para o formulário de criação de regra
    var selectedRuleType by mutableStateOf(RuleType.CONTROLO_VELOCIDADE)
    var ruleValueInput by mutableStateOf("") // Ex: "120" para velocidade
    var ruleDescription by mutableStateOf("")

    var currentRules by mutableStateOf<List<Rule>>(emptyList())
        private set

    var monitorAlertHistory by mutableStateOf<List<Alert>>(emptyList())

    fun fetchMonitorAlertHistory(monitoredUsers: List<User>) {
        // Extraímos apenas os IDs da lista de objetos User
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
            fetchRulesForProtected(protectedId) // Atualiza a lista
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
            fetchRulesForProtected(protectedId) // Atualiza a lista
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

            // E o centro é a última localização do protegido
            // Se ele nunca tiver ligado o GPS, usamos 0.0 ou null (e a regra não fica ativa logo)
            finalLat = targetUser.lastLatitude
            finalLon = targetUser.lastLongitude

            if (finalLat == null || finalLon == null) {
                // Opcional: Avisar erro ou assumir coordenadas de Lisboa/Coimbra por defeito
            }
        } else {
            // Para Velocidade e Inatividade, usamos o valueDouble normal
            finalValue = valueInputDouble
        }

        val newRule = Rule(
            type = selectedRuleType,
            description = ruleDescription.ifBlank { "Regra de ${selectedRuleType.name}" },
            isActive = false, // Começa inativa
            monitorId = monitorId,
            protectedId = targetUser.id,

            // Preenchemos os campos corretos consoante o tipo
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
            } else {
                // Log de erro
            }
        }
    }
}