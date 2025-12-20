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

            isLoading = false

            if (result.isSuccess) {
                Log.d("SafetySec", "SUCESSO: Regra gravada no Firestore! ID: ${newRule.type}")

                // Só limpamos e fechamos se tiver corrido bem
                ruleValueInput = ""
                ruleDescription = ""
                onFinished()
            } else {
                val erro = result.exceptionOrNull()?.message
                Log.e("SafetySec", "ERRO: Falha ao gravar regra: $erro")
            }
        }
    }



}