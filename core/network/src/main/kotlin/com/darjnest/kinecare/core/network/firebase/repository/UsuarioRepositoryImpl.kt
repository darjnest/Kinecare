@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import javax.inject.Inject
import kotlin.time.Clock

private const val COLECCION_USUARIOS = "usuarios"

/**
 * Implementacion Firestore de `usuarios/{usuarioId}` (docs/DATA_MODEL.md).
 * Vive en `:core:network` (no en la feature que la consume) porque mas de
 * una feature necesita el mismo documento — ver el comentario de
 * `UsuarioRepository` en `:core:common`.
 */
class UsuarioRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UsuarioRepository {

    override suspend fun obtenerPorId(id: String): Result<Usuario, UsuarioError> {
        return try {
            val doc = firestore.collection(COLECCION_USUARIOS).document(id).get().await()
            val usuario = doc.aUsuarioONull(id) ?: return Result.Error(UsuarioError.NO_ENCONTRADO)
            Result.Success(usuario)
        } catch (e: FirebaseNetworkException) {
            Result.Error(UsuarioError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(UsuarioError.DESCONOCIDO)
        }
    }

    override suspend fun actualizarDatosPersonales(
        id: String,
        nombre: String,
        telefono: String?,
        correoContacto: String?,
    ): Result<Unit, UsuarioError> {
        return try {
            firestore.collection(COLECCION_USUARIOS).document(id)
                .update(
                    mapOf(
                        "nombre" to nombre,
                        "telefono" to telefono,
                        "correoContacto" to correoContacto,
                    ),
                )
                .await()
            Result.Success(Unit)
        } catch (e: FirebaseNetworkException) {
            Result.Error(UsuarioError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(UsuarioError.DESCONOCIDO)
        }
    }
}

/** Mapea un `DocumentSnapshot` de `usuarios/{id}` a [Usuario], o null si no existe. */
internal fun DocumentSnapshot.aUsuarioONull(id: String): Usuario? {
    if (!exists()) return null
    return Usuario(
        id = id,
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
