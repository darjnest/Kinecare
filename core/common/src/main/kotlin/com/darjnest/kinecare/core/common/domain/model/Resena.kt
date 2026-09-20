package com.darjnest.kinecare.core.common.domain.model

import kotlinx.datetime.Instant

data class Resena(
    val id: String,
    val reservaId: String,
    val clienteId: String,
    val profesionalId: String,
    val calificacion: Int,
    val comentario: String?,
    val fecha: Instant,
    val respuestaProfesional: String?,
)
