package com.darjnest.kinecare.core.common.domain.model

import kotlinx.datetime.Instant

enum class RolUsuario {
    CLIENTE,
    PROFESIONAL,
}

data class Usuario(
    val id: String,
    val nombre: String,
    val rut: String,
    val email: String,
    val correoContacto: String?,
    val telefono: String?,
    val rol: RolUsuario,
    val fotoUrl: String?,
    val fechaRegistro: Instant,
)
