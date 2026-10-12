package com.darjnest.kinecare.core.network.functions.dto

import kotlinx.serialization.Serializable

/** `data` de `cancelarReserva`; `motivo` es opcional. */
@Serializable
data class CancelarReservaRequestDto(
    val reservaId: String,
    val motivo: String? = null,
)

/** `estado` es `CANCELADA_CLIENTE` o `CANCELADA_PROFESIONAL`; `reembolso` es `NO_APLICA`, `TOTAL` o `NINGUNO`. */
@Serializable
data class CancelarReservaResultadoDto(
    val estado: String,
    val reembolso: String? = null,
)
