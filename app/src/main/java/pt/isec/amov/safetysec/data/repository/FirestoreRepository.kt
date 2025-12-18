package pt.isec.amov.safetysec.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.data.model.Rule
import pt.isec.amov.safetysec.data.model.Alert

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()

    // --- ASSOCIAÇÃO (Mecanismo OTP) ---

    /**
     * Gera e guarda um código de associação no perfil do Protegido [cite: 18]
     */

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
            // 1. Procurar quem tem este código
            val query = db.collection("users")
                .whereEqualTo("connectionCode", codeInput)
                .get()
                .await()

            if (query.isEmpty) throw Exception("Código inválido ou já utilizado.")

            val protectedDoc = query.documents.first()
            val protectedId = protectedDoc.id

            // VERIFICAR SE É ELE PROPRIO
            if (protectedId == monitorId) {
                throw Exception("Não pode associar-se a si próprio!")
            }


            // 2. Operação Atómica para ligar e apagar o código
            val batch = db.batch()
            val monitorRef = db.collection("users").document(monitorId)
            val protectedRef = db.collection("users").document(protectedId)

            // Adiciona IDs às listas de associação (Requisito de monitorização)
            batch.update(monitorRef, "myProtectedUsers", FieldValue.arrayUnion(protectedId))
            batch.update(protectedRef, "myMonitors", FieldValue.arrayUnion(monitorId))

            // PONTO 4: Apagar o código para que expire após este uso único
            batch.update(protectedRef, "connectionCode", null)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cria a ligação bi-direcional entre Monitor e Protegido
     */
    suspend fun linkUsers(monitorId: String, protectedId: String): Result<Unit> {
        return try {
            val batch = db.batch()

            val monitorRef = db.collection("users").document(monitorId)
            val protectedRef = db.collection("users").document(protectedId)

            // Adiciona à lista de cada um usando FieldValue.arrayUnion para evitar duplicados
            batch.update(monitorRef, "associatedProtegidoIds", FieldValue.arrayUnion(protectedId))
            batch.update(protectedRef, "associatedMonitorIds", FieldValue.arrayUnion(monitorId))

            // Remove o código de associação usado [cite: 18]
            batch.update(protectedRef, "connectionCode", null)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- REGRAS DE MONITORIZAÇÃO ---

    /**
     * O Monitor cria ou altera uma regra [cite: 22]
     */
    suspend fun upsertRule(rule: Rule): Result<Unit> {
        return try {
            db.collection("rules").document(rule.id).set(rule).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * O Protegido autoriza ou revoga uma regra individualmente [cite: 23, 47]
     */
    suspend fun setRuleStatus(ruleId: String, isEnabled: Boolean): Result<Unit> {
        return try {
            db.collection("rules").document(ruleId)
                .update("ativa", isEnabled).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- GESTÃO DE ALERTAS ---

    /**
     * Regista um novo alerta com localização e dados do evento [cite: 34, 35]
     */
    suspend fun createAlert(alert: Alert): Result<Unit> {
        return try {
            db.collection("alerts").document(alert.id).set(alert).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}