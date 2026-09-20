package com.darjnest.kinecare.core.common.domain.model

import kotlinx.datetime.Instant

enum class TipoInsignia {
    IDENTIDAD,
    CREDENCIALES,
    AUTENTICIDAD,
    HISTORIAL,
}

data class Insignia(
    val tipo: TipoInsignia,
    val estado: EstadoVerificacion,
    val detalle: String?,
    val fechaActualizacion: Instant,
)
