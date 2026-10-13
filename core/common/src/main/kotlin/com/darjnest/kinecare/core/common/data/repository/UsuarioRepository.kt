package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.domain.model.PerfilPublico
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result

/**
 * Acceso a `usuarios/{usuarioId}` (ver docs/DATA_MODEL.md). Vive en
 * `:core:common` (interfaz) + `:core:network` (implementacion con
 * Firestore) porque mas de una feature necesita el documento propio del
 * usuario autenticado (`:feature:auth` ya lo resuelve por su cuenta dentro
 * de `AuthRepository`; `:feature:client-panel` y `:feature:search` lo
 * necesitan tambien para resolver el usuario detras de un `Profesional`) —
 * las features nunca se dependen entre si, asi que lo compartido va a
 * `:core:*` (docs/ARCHITECTURE.md).
 */
interface UsuarioRepository {
    /**
     * `usuarios/{id}` completo, con rut, correo y telefono. Es privado: firestore.rules solo deja
     * leerlo a su dueno, asi que [id] debe ser el del usuario autenticado. Para mostrar a otra
     * persona se usa [obtenerPerfilPublico].
     */
    suspend fun obtenerPorId(id: String): Result<Usuario, UsuarioError>

    /** Nombre y foto de cualquier usuario (`perfilesPublicos/{id}`), legibles por todo usuario autenticado. */
    suspend fun obtenerPerfilPublico(id: String): Result<PerfilPublico, UsuarioError>

    /**
     * Actualiza nombre/telefono/correoContacto de `usuarios/{id}` y el nombre de su perfil
     * publico, en un batch. El `rut`
     * no es editable (identificador de login, ver docs/DOMAIN.md) y el
     * `rol` nunca se escribe desde el cliente (firestore.rules lo bloquea).
     */
    suspend fun actualizarDatosPersonales(
        id: String,
        nombre: String,
        telefono: String?,
        correoContacto: String?,
    ): Result<Unit, UsuarioError>

    /**
     * Guarda en `usuarios/{id}.fotoUrl` y en el perfil publico la URL de la foto de perfil recien
     * subida a Storage (`FotoPerfilRepository.subir`), en un batch. Un [fotoUrl] nulo la quita.
     */
    suspend fun actualizarFotoUrl(id: String, fotoUrl: String?): Result<Unit, UsuarioError>
}
