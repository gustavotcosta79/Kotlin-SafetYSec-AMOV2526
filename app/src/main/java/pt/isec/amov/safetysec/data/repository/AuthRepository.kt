package pt.isec.amov.safetysec.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import pt.isec.amov.safetysec.data.model.User

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    suspend fun register(name: String, email: String, pass: String, isMonitor: Boolean, isProtected: Boolean): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val uid = result.user?.uid ?: throw Exception("Erro no UID")

            val newUser = User(
                id = uid,
                name = name,
                email = email,
                isMonitor = isMonitor,
                isProtected = isProtected,
                cancellationCode = "1234" // Requisito: código de cancelamento [cite: 32, 33]
            )

            db.collection("users").document(uid).set(newUser).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, pass: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, pass).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(uid: String): User? {
        return try {
            val snapshot = db.collection("users").document(uid).get().await()
            snapshot.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }


    fun logout() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            // Em logout, erros são raros, mas convém logar se necessário
        }
    }
}