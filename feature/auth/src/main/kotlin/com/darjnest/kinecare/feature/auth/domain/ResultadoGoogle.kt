package com.darjnest.kinecare.feature.auth.domain

import com.darjnest.kinecare.core.common.domain.model.Usuario

/**
 * Google solo entrega nombre/correo/foto: nunca un RUT. Si ya existe un
 * documento `usuarios/{uid}` la sesión queda lista; si no, la UI debe pedir
 * el RUT, teléfono y rol antes de crear la cuenta (ver `completarRegistroGoogle`
 * en `AuthRepository`).
 */
sealed interface ResultadoGoogle {
    data class SesionIniciada(val usuario: Usuario) : ResultadoGoogle

    data class RequiereCompletarPerfil(
        val uid: String,
        val nombreSugerido: String,
        val correoGoogle: String,
        val fotoUrl: String?,
    ) : ResultadoGoogle
}
