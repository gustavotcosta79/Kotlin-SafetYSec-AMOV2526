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

            // Corrigido para os nomes do teu modelo:
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

    // Nova função para listar os protegidos com os nomes correctos
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
            // Cria um ID automático para o alerta se não tiver
            val docRef = db.collection("alerts").document()
            val alertWithId = alert.copy(id = docRef.id)

            // gravamos o objeto mas com o ID gerado pelo Firestore
            db.collection("alerts").document(docRef.id)
                .set(alertWithId)
                .await()

            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelAlert (alertId: String) : Result<Unit>{
        return try {
            db.collection("alerts").document(alertId)
                .update(
                    mapOf(
                        "cancelled" to true,
                        "solved" to true
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception){
            Result.failure(e)
        }
    }

    fun listenForAlerts(protectedIds: List<String>, onAlertsReceived: (List<Alert>) -> Unit) {
        if (protectedIds.isEmpty()) return

        db.collection("alerts")
            .whereIn("userEmail", protectedIds) // Filtra pelos ecrãs que o monitor vigia
            .whereEqualTo("solved", false)      // Apenas alertas ativos
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

    suspend fun updateUserProfile (userId : String, updates: Map<String, Any>): Result<Unit>{
        return try {
            db.collection("users").document(userId).update(updates).await()
            Result.success(Unit)
        }catch (e : Exception){
            Result.failure(e)
        }
    }

}
