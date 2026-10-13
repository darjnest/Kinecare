@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.PerfilPublico
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
private const val COLECCION_PERFILES_PUBLICOS = "perfilesPublicos"

/**
 * Implementacion Firestore de `usuarios/{usuarioId}` (privado) y de
 * `perfilesPublicos/{usuarioId}` (nombre y foto, legibles por todos;
 * docs/DATA_MODEL.md). Toda escritura de nombre o foto toca ambos en un
 * batch para que no diverjan.
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

    override suspend fun obtenerPerfilPublico(id: String): Result<PerfilPublico, UsuarioError> {
        return try {
            val doc = firestore.collection(COLECCION_PERFILES_PUBLICOS).document(id).get().await()
            if (!doc.exists()) return Result.Error(UsuarioError.NO_ENCONTRADO)
            Result.Success(PerfilPublico(id = id, nombre = doc.getString("nombre") ?: "", fotoUrl = doc.getString("fotoUrl")))
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
            val batch = firestore.batch()
            batch.update(
                firestore.collection(COLECCION_USUARIOS).document(id),
                mapOf(
                    "nombre" to nombre,
                    "telefono" to telefono,
                    "correoContacto" to correoContacto,
                ),
            )
            batch.update(firestore.collection(COLECCION_PERFILES_PUBLICOS).document(id), mapOf("nombre" to nombre))
            batch.commit().await()
            Result.Success(Unit)
        } catch (e: FirebaseNetworkException) {
            Result.Error(UsuarioError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(UsuarioError.DESCONOCIDO)
        }
    }

    override suspend fun actualizarFotoUrl(id: String, fotoUrl: String?): Result<Unit, UsuarioError> {
        return try {
            val batch = firestore.batch()
            batch.update(firestore.collection(COLECCION_USUARIOS).document(id), mapOf("fotoUrl" to fotoUrl))
            batch.update(firestore.collection(COLECCION_PERFILES_PUBLICOS).document(id), mapOf("fotoUrl" to fotoUrl))
            batch.commit().await()
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
