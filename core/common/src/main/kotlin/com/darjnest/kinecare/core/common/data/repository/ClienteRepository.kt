package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.ClienteError
import com.darjnest.kinecare.core.common.domain.model.Cliente
import com.darjnest.kinecare.core.common.result.Result

/**
 * Acceso a `clientes/{usuarioId}` (ver docs/DATA_MODEL.md): direcciones,
 * metodos de pago (solo tokens) y favoritos. Vive en `:core:common` +
 * `:core:network` por la misma razon que [UsuarioRepository].
 */
interface ClienteRepository {
    suspend fun obtenerPorId(id: String): Result<Cliente, ClienteError>

    /** `arrayUnion` sobre `clientes/{clienteId}.favoritos`. */
    suspend fun agregarFavorito(clienteId: String, profesionalId: String): Result<Unit, ClienteError>

    /** `arrayRemove` sobre `clientes/{clienteId}.favoritos`. */
    suspend fun quitarFavorito(clienteId: String, profesionalId: String): Result<Unit, ClienteError>
}
