package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.ClienteError
import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.data.repository.ClienteRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.Cliente
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.MetodoPago
import com.darjnest.kinecare.core.common.domain.model.TipoMetodoPago
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val COLECCION_CLIENTES = "clientes"

/**
 * Implementacion Firestore de `clientes/{usuarioId}` (docs/DATA_MODEL.md).
 * Reusa [UsuarioRepository] para el `Usuario` 1:1 en vez de repetir el
 * mapeo de `usuarios/{id}` (mismo documento que ya resuelve
 * `ProfesionalRepositoryImpl`).
 */
class ClienteRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val usuarioRepository: UsuarioRepository,
) : ClienteRepository {

    override suspend fun obtenerPorId(id: String): Result<Cliente, ClienteError> {
        return try {
            val usuario = when (val resultado = usuarioRepository.obtenerPorId(id)) {
                is Result.Error -> return Result.Error(resultado.error.aClienteError())
                is Result.Success -> resultado.data
            }
            val doc = firestore.collection(COLECCION_CLIENTES).document(id).get().await()
            if (!doc.exists()) return Result.Error(ClienteError.NO_ENCONTRADO)

            @Suppress("UNCHECKED_CAST")
            val direcciones = (doc.get("direcciones") as? List<Map<String, Any?>>)
                ?.map { it.aDireccion() }
                ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val metodosPago = (doc.get("metodosPago") as? List<Map<String, Any?>>)
                ?.mapNotNull { it.aMetodoPagoONull() }
                ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val favoritos = (doc.get("favoritos") as? List<Any?>)
                ?.filterIsInstance<String>()
                ?: emptyList()

            Result.Success(
                Cliente(
                    usuario = usuario,
                    direcciones = direcciones,
                    metodosPago = metodosPago,
                    favoritos = favoritos,
                ),
            )
        } catch (e: FirebaseNetworkException) {
            Result.Error(ClienteError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(ClienteError.DESCONOCIDO)
        }
    }

    override suspend fun agregarFavorito(clienteId: String, profesionalId: String): Result<Unit, ClienteError> =
        actualizarFavoritos(clienteId, FieldValue.arrayUnion(profesionalId))

    override suspend fun quitarFavorito(clienteId: String, profesionalId: String): Result<Unit, ClienteError> =
        actualizarFavoritos(clienteId, FieldValue.arrayRemove(profesionalId))

    private suspend fun actualizarFavoritos(clienteId: String, valor: FieldValue): Result<Unit, ClienteError> {
        return try {
            firestore.collection(COLECCION_CLIENTES).document(clienteId)
                .update("favoritos", valor)
                .await()
            Result.Success(Unit)
        } catch (e: FirebaseNetworkException) {
            Result.Error(ClienteError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(ClienteError.DESCONOCIDO)
        }
    }
}

private fun UsuarioError.aClienteError(): ClienteError = when (this) {
    UsuarioError.SIN_INTERNET -> ClienteError.SIN_INTERNET
    UsuarioError.NO_ENCONTRADO -> ClienteError.NO_ENCONTRADO
    UsuarioError.DESCONOCIDO -> ClienteError.DESCONOCIDO
}

private fun Map<String, Any?>.aDireccion(): Direccion = Direccion(
    calle = this["calle"] as? String ?: "",
    numero = this["numero"] as? String ?: "",
    comuna = this["comuna"] as? String ?: "",
    ciudad = this["ciudad"] as? String ?: "",
    lat = this["lat"] as? Double,
    lng = this["lng"] as? Double,
    indicaciones = this["indicaciones"] as? String,
)

private fun Map<String, Any?>.aMetodoPagoONull(): MetodoPago? {
    val tipo = runCatching { TipoMetodoPago.valueOf(this["tipo"] as? String ?: "") }.getOrNull() ?: return null
    return MetodoPago(
        tipo = tipo,
        ultimosDigitos = this["ultimosDigitos"] as? String,
        tokenPasarela = this["tokenPasarela"] as? String ?: "",
    )
}
