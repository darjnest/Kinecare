package com.darjnest.kinecare.feature.auth.data.repository

import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.auth.domain.AuthError
import com.darjnest.kinecare.feature.auth.domain.ResultadoGoogle
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun iniciarSesion(rut: String, password: String): Result<Usuario, AuthError>

    suspend fun registrar(
        nombre: String,
        rut: String,
        password: String,
        rol: RolUsuario,
        telefono: String,
        correoContacto: String,
    ): Result<Usuario, AuthError>

    /** Autentica en Firebase con el ID token de Google. No crea la cuenta:
     * si `usuarios/{uid}` no existe todavía pide completar el perfil. */
    suspend fun iniciarSesionConGoogle(idToken: String): Result<ResultadoGoogle, AuthError>

    /** Crea `usuarios/{uid}` para una cuenta ya autenticada con Google. */
    suspend fun completarRegistroGoogle(
        uid: String,
        nombre: String,
        rut: String,
        telefono: String,
        correoContacto: String,
        rol: RolUsuario,
        fotoUrl: String?,
    ): Result<Usuario, AuthError>

    fun observarUsuarioActual(): Flow<Usuario?>

    suspend fun cerrarSesion()
}
