package com.darjnest.kinecare.feature.auth.data.repository

import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.auth.domain.AuthError
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun iniciarSesion(rut: String, password: String): Result<Usuario, AuthError>

    suspend fun registrar(
        nombre: String,
        rut: String,
        password: String,
        rol: RolUsuario,
    ): Result<Usuario, AuthError>

    fun observarUsuarioActual(): Flow<Usuario?>

    suspend fun cerrarSesion()
}
