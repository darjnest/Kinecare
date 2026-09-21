@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.auth.data.repository_impl

import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.domain.util.RutUtils
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.auth.data.repository.AuthRepository
import com.darjnest.kinecare.feature.auth.domain.AuthError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import javax.inject.Inject
import kotlin.time.Clock

private const val COLECCION_USUARIOS = "usuarios"

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    override suspend fun iniciarSesion(rut: String, password: String): Result<Usuario, AuthError> {
        return try {
            val resultado = firebaseAuth.signInWithEmailAndPassword(RutUtils.emailFirebase(rut), password).await()
            val uid = resultado.user?.uid ?: return Result.Error(AuthError.DESCONOCIDO)
            obtenerUsuario(uid)
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.Error(AuthError.USUARIO_NO_ENCONTRADO)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.Error(AuthError.CREDENCIALES_INVALIDAS)
        } catch (e: FirebaseNetworkException) {
            Result.Error(AuthError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(AuthError.DESCONOCIDO)
        }
    }

    override suspend fun registrar(
        nombre: String,
        rut: String,
        password: String,
        rol: RolUsuario,
        telefono: String,
        correoContacto: String,
    ): Result<Usuario, AuthError> {
        return try {
            val rutNormalizado = RutUtils.normalizar(rut)
            val resultado = firebaseAuth.createUserWithEmailAndPassword(RutUtils.emailFirebase(rutNormalizado), password).await()
            val uid = resultado.user?.uid ?: return Result.Error(AuthError.DESCONOCIDO)

            val datosUsuario = mapOf(
                "nombre" to nombre,
                "rut" to rutNormalizado,
                "email" to RutUtils.emailFirebase(rutNormalizado),
                "correoContacto" to correoContacto,
                "telefono" to telefono,
                "rol" to rol.name,
                "fotoUrl" to null,
                "fechaRegistro" to FieldValue.serverTimestamp(),
            )
            firestore.collection(COLECCION_USUARIOS).document(uid).set(datosUsuario).await()

            obtenerUsuario(uid)
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.Error(AuthError.RUT_YA_REGISTRADO)
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.Error(AuthError.PASSWORD_DEBIL)
        } catch (e: FirebaseNetworkException) {
            Result.Error(AuthError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(AuthError.DESCONOCIDO)
        }
    }

    override fun observarUsuarioActual(): Flow<Usuario?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid == null) {
                trySend(null)
            } else {
                firestore.collection(COLECCION_USUARIOS).document(uid).get()
                    .addOnSuccessListener { doc -> trySend(doc.aUsuarioONull(uid)) }
                    .addOnFailureListener { trySend(null) }
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun cerrarSesion() {
        firebaseAuth.signOut()
    }

    private suspend fun obtenerUsuario(uid: String): Result<Usuario, AuthError> {
        return try {
            val doc = firestore.collection(COLECCION_USUARIOS).document(uid).get().await()
            val usuario = doc.aUsuarioONull(uid) ?: return Result.Error(AuthError.USUARIO_NO_ENCONTRADO)
            Result.Success(usuario)
        } catch (e: Exception) {
            Result.Error(AuthError.DESCONOCIDO)
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.aUsuarioONull(uid: String): Usuario? {
        if (!exists()) return null
        return Usuario(
            id = uid,
            nombre = getString("nombre") ?: "",
            rut = getString("rut") ?: "",
            email = getString("email") ?: "",
            correoContacto = getString("correoContacto"),
            telefono = getString("telefono"),
            rol = runCatching { RolUsuario.valueOf(getString("rol") ?: "") }.getOrDefault(RolUsuario.CLIENTE),
            fotoUrl = getString("fotoUrl"),
            fechaRegistro = getTimestamp("fechaRegistro")?.let { Instant.fromEpochMilliseconds(it.toDate().time) }
                ?: Clock.System.now(),
        )
    }
}
