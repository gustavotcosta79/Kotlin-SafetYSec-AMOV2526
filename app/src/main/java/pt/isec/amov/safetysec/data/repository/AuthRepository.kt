package pt.isec.amov.safetysec.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import pt.isec.amov.safetysec.data.model.User

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    suspend fun register(
        name: String,
        email: String,
        pass: String,
        isMonitor: Boolean,
        isProtected: Boolean,
        cancellationCode: String
    ): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val uid = result.user?.uid ?: throw Exception("Erro ao gerar identificador de utilizador.")

            val newUser = User(
                id = uid,
                name = name,
                email = email,
                isMonitor = isMonitor,
                isProtected = isProtected,
                cancellationCode = cancellationCode
            )

            db.collection("users").document(uid).set(newUser).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, pass: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, pass).await()
            val uid = authResult.user?.uid ?: throw Exception("Utilizador não autenticado.")

            val user = getUserProfile(uid)
                ?: throw Exception("Perfil não encontrado na base de dados.")

            Result.success(user)
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
        auth.signOut()
    }
}