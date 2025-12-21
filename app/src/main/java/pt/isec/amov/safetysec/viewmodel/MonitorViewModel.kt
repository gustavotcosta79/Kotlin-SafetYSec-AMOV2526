package pt.isec.amov.safetysec.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.data.model.Rule
import pt.isec.amov.safetysec.data.model.RuleType
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



    fun submitRule(monitorId: String, protectedId: String, onFinished: () -> Unit) {
        isLoading = true

        val valueDouble = ruleValueInput.toDoubleOrNull()

        val newRule = Rule(
            type = selectedRuleType,
            description = ruleDescription.ifBlank { "Regra de ${selectedRuleType.name}" },
            valueDouble = valueDouble,
            monitorId = monitorId,
            protectedId = protectedId,
            isActive = false
        )

        viewModelScope.launch {
            // Agora capturamos o resultado
            val result = repository.proposeRule(newRule)


            if (result.isSuccess) {
                //vamos buscar as novas regras à bd para mostrar logo na lista
                val updateRules = repository.getRulesForUser(protectedId)

                currentRules = updateRules.getOrNull() ?: emptyList()

                // Só limpamos e fechamos se tiver corrido bem
                ruleValueInput = ""
                ruleDescription = ""
                onFinished()
            } else {
                val erro = result.exceptionOrNull()?.message
                Log.e("SafetySec", "ERRO: Falha ao gravar regra: $erro")
            }
            isLoading = false

        }
    }
}