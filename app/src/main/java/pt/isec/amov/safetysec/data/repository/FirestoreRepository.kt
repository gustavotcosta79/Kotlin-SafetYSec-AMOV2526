package pt.isec.amov.safetysec.data.repository

import androidx.compose.runtime.Updater
import com.google.android.gms.common.api.internal.ApiExceptionMapper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.data.model.Rule
import pt.isec.amov.safetysec.data.model.Alert

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()

    // --- ASSOCIAÇÃO (Mecanismo OTP) ---

    // Ponto 2 e 4: Gravar o código gerado
    suspend fun updateConnectionCode(userId: String, code: String): Result<Unit> {
        return try {
            db.collection("users").document(userId)
                .update("connectionCode", code).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Ponto 3 e 4: O Monitor usa o código, a ligação é feita e o código APAGADO
    suspend fun linkMonitorToProtected(monitorId: String, codeInput: String): Result<Unit> {
        return try {
            val query = db.collection("users")
                .whereEqualTo("connectionCode", codeInput)
                .get()
                .await()

            if (query.isEmpty) throw Exception("Código inválido ou já utilizado.")

            val protectedDoc = query.documents.first()
            val protectedId = protectedDoc.id

            if (protectedId == monitorId) {
                throw Exception("Não pode associar-se a si próprio!")
            }

            val batch = db.batch()
            val monitorRef = db.collection("users").document(monitorId)
            val protectedRef = db.collection("users").document(protectedId)

            batch.update(monitorRef, "associatedProtegidoIds", FieldValue.arrayUnion(protectedId))
            batch.update(protectedRef, "associatedMonitorIds", FieldValue.arrayUnion(monitorId))

            // Apaga o código após o uso (OTP)
            batch.update(protectedRef, "connectionCode", null)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Função para listar os protegidos com os nomes correctos
    suspend fun getAssociatedUsers(uids: List<String>): Result<List<User>> {
        return try {
            if (uids.isEmpty()) return Result.success(emptyList())

            val snapshot = db.collection("users")
                .whereIn("id", uids)
                .get()
                .await()

            val users = snapshot.toObjects(User::class.java)
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Função para criar o Alerta na Base de Dados
    suspend fun createAlert(alert: Alert): Result<String> {
        return try {
            val docRef = db.collection("alerts").document()
            val alertWithId = alert.copy(id = docRef.id)

            db.collection("alerts").document(docRef.id)
                .set(alertWithId)
                .await()

            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CANCELAMENTO DE ALERTAS ---

    // Cancelar um alerta específico (Mantém-se por compatibilidade)
    suspend fun cancelAlert(alertId: String): Result<Unit> {
        return try {
            db.collection("alerts").document(alertId)
                .update(
                    mapOf(
                        "cancelled" to true,
                        "solved" to true
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // NOVA FUNÇÃO: Cancela TODOS os alertas ativos para este utilizador (Corrige o bug de sobreposição)
    suspend fun cancelAllActiveAlerts(protectedId: String): Result<Unit> {
        return try {
            // 1. Procurar todos os alertas deste user que AINDA não estão resolvidos
            val snapshot = db.collection("alerts")
                .whereEqualTo("protectedId", protectedId)
                .whereEqualTo("solved", false)
                .get()
                .await()

            if (snapshot.isEmpty) return Result.success(Unit)

            // 2. Criar um lote (batch) para fechar todos ao mesmo tempo
            val batch = db.batch()

            for (document in snapshot.documents) {
                batch.update(document.reference, mapOf(
                    "cancelled" to true,
                    "solved" to true
                ))
            }

            // 3. Executar
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenForAlerts(protectedIds: List<String>, onAlertsReceived: (List<Alert>) -> Unit) {
        if (protectedIds.isEmpty()) return

        db.collection("alerts")
            .whereIn("protectedId", protectedIds)
            .whereEqualTo("solved", false)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val alerts = snapshot?.toObjects(Alert::class.java) ?: emptyList()
                onAlertsReceived(alerts)
            }
    }

    suspend fun updateLastLocation(userId: String, lat: Double, lon: Double, time: Long): Result<Unit> {
        return try {
            db.collection("users").document(userId)
                .update(
                    "lastLatitude", lat,
                    "lastLongitude", lon,
                    "lastLocationTime", time
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            db.collection("users").document(userId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // 1. Monitor propõe a regra
    suspend fun proposeRule(rule: Rule): Result<Unit> {
        return try {
            val docRef = db.collection("rules").document()
            val ruleWithId = rule.copy(id = docRef.id)
            docRef.set(ruleWithId).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // 2. Protegido ou Monitor listam as regras
    fun listenToRules(protectedId: String, onUpdate: (List<Rule>) -> Unit) {
        db.collection("rules")
            .whereEqualTo("protectedId", protectedId)
            .addSnapshotListener { snapshot, _ ->
                val rules = snapshot?.toObjects(Rule::class.java) ?: emptyList()
                onUpdate(rules)
            }
    }

    // 3. Protegido aceita ou revoga a regra
    suspend fun updateRuleStatus(ruleId: String, isEnabled: Boolean): Result<Unit> {
        return try {
            db.collection("rules").document(ruleId)
                .update("isActive", isEnabled).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }


    // Função para ler as regras de um Protegido específico (CRIADAS PELO MONITOR X)
    suspend fun getRulesForUser(protectedId: String, monitorId: String): Result<List<Rule>> {
        return try {
            val snapshot = db.collection("rules")
                .whereEqualTo("protectedId", protectedId)
                .whereEqualTo("monitorId", monitorId)
                .get()
                .await()

            val rules = snapshot.toObjects(Rule::class.java)
            Result.success(rules)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeAssociation(monitorId: String, protectedId: String): Result<Unit> {
        return try {
            val rulesSnapshot = db.collection("rules")
                .whereEqualTo("monitorId", monitorId)
                .whereEqualTo("protectedId", protectedId)
                .get()
                .await()

            val batch = db.batch()

            for (ruleDoc in rulesSnapshot.documents) {
                batch.delete(ruleDoc.reference)
            }

            val monitorRef = db.collection("users").document(monitorId)
            val protectedRef = db.collection("users").document(protectedId)

            batch.update(monitorRef, "associatedProtegidoIds", FieldValue.arrayRemove(protectedId))
            batch.update(protectedRef, "associatedMonitorIds", FieldValue.arrayRemove(monitorId))

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRule(ruleId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            db.collection("rules").document(ruleId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRule(ruleId: String): Result<Unit> {
        return try {
            db.collection("rules").document(ruleId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Obter histórico de alertas de um protegido
    suspend fun getAlertHistory(protectedId: String): Result<List<Alert>> {
        return try {
            val snapshot = db.collection("alerts")
                .whereEqualTo("protectedId", protectedId)
                .get()
                .await()

            val alerts = snapshot.toObjects(Alert::class.java).sortedByDescending { it.date }
            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Obter histórico global de VÁRIOS protegidos (para o Monitor)
    suspend fun getAlertsForMonitor(protectedIds: List<String>): Result<List<Alert>> {
        if (protectedIds.isEmpty()) return Result.success(emptyList())

        return try {
            val snapshot = db.collection("alerts")
                .whereIn("protectedId", protectedIds)
                .get()
                .await()

            val alerts = snapshot.toObjects(Alert::class.java).sortedByDescending { it.date }
            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Atualizar um alerta existente com o URL/Caminho do vídeo
    suspend fun updateAlertVideo(alertId: String, videoUrl: String): Result<Unit> {
        return try {
            db.collection("alerts").document(alertId)
                .update("videoUrl", videoUrl).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Adicionar Janela
    suspend fun addTimeWindow(userId: String, window: pt.isec.amov.safetysec.data.model.TimeWindow) {
        try {
            val ref = db.collection("users").document(userId).collection("time_windows").document()
            val windowWithId = window.copy(id = ref.id)
            ref.set(windowWithId).await()
        } catch (e: Exception) { e.printStackTrace() }
    }

    // Obter Janelas (Ouve em tempo real)
    fun listenToTimeWindows(userId: String, onUpdate: (List<pt.isec.amov.safetysec.data.model.TimeWindow>) -> Unit) {
        db.collection("users").document(userId).collection("time_windows")
            .addSnapshotListener { snapshot, _ ->
                val windows = snapshot?.toObjects(pt.isec.amov.safetysec.data.model.TimeWindow::class.java) ?: emptyList()
                onUpdate(windows)
            }
    }

    // Apagar Janela
    suspend fun deleteTimeWindow(userId: String, windowId: String) {
        try {
            db.collection("users").document(userId).collection("time_windows")
                .document(windowId).delete().await()
        } catch (e: Exception) { e.printStackTrace() }
    }
}