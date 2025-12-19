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
}
