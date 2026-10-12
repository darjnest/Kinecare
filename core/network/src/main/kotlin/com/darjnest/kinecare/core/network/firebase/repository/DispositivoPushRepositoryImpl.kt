package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.DispositivoPushError
import com.darjnest.kinecare.core.common.data.repository.DispositivoPushRepository
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val COLECCION_USUARIOS = "usuarios"
private const val SUBCOLECCION_DISPOSITIVOS = "dispositivos"
private const val PLATAFORMA_ANDROID = "ANDROID"

/**
 * Registra el token de FCM en `usuarios/{uid}/dispositivos/{token}` con
 * `{plataforma: 'ANDROID', actualizadoEn: serverTimestamp}` (docs/DATA_MODEL.md). El id del
 * documento es el token: registrar dos veces es idempotente y solo refresca `actualizadoEn`.
 */
class DispositivoPushRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val messaging: FirebaseMessaging,
) : DispositivoPushRepository {

    override suspend fun registrarDispositivoActual(): Result<Unit, DispositivoPushError> {
        val uid = firebaseAuth.currentUser?.uid ?: return Result.Error(DispositivoPushError.SIN_SESION)
        return ejecutar {
            val token = obtenerToken() ?: return@ejecutar Result.Error(DispositivoPushError.SIN_TOKEN)
            escribir(uid, token)
        }
    }

    override suspend fun registrarToken(token: String): Result<Unit, DispositivoPushError> {
        val uid = firebaseAuth.currentUser?.uid ?: return Result.Error(DispositivoPushError.SIN_SESION)
        if (token.isBlank()) return Result.Error(DispositivoPushError.SIN_TOKEN)
        return ejecutar { escribir(uid, token) }
    }

    override suspend fun eliminarDispositivoActual(): Result<Unit, DispositivoPushError> {
        val uid = firebaseAuth.currentUser?.uid ?: return Result.Error(DispositivoPushError.SIN_SESION)
        return ejecutar {
            val token = obtenerToken() ?: return@ejecutar Result.Error(DispositivoPushError.SIN_TOKEN)
            // Primero el documento (requiere la sesion); el token de FCM se invalida aunque el borrado falle.
            val borrado = try {
                documento(uid, token).delete().await()
                Result.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(e.aDispositivoPushError())
            }
            try {
                messaging.deleteToken().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sin red, FCM lo reintenta; el documento ya quedo gestionado arriba.
            }
            borrado
        }
    }

    private suspend fun obtenerToken(): String? = messaging.token.await()?.takeIf { it.isNotBlank() }

    private fun documento(uid: String, token: String) =
        firestore.collection(COLECCION_USUARIOS).document(uid)
            .collection(SUBCOLECCION_DISPOSITIVOS).document(token)

    private suspend fun escribir(uid: String, token: String): Result<Unit, DispositivoPushError> {
        documento(uid, token)
            .set(mapOf("plataforma" to PLATAFORMA_ANDROID, "actualizadoEn" to FieldValue.serverTimestamp()))
            .await()
        return Result.Success(Unit)
    }

    private suspend fun ejecutar(bloque: suspend () -> Result<Unit, DispositivoPushError>): Result<Unit, DispositivoPushError> =
        try {
            bloque()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e.aDispositivoPushError())
        }
}

private fun Throwable.aDispositivoPushError(): DispositivoPushError = when (this) {
    is FirebaseNetworkException -> DispositivoPushError.SIN_INTERNET
    else -> DispositivoPushError.DESCONOCIDO
}
