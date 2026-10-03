package com.darjnest.kinecare.core.network.functions.dto

import kotlinx.serialization.Serializable

/** `data` de `responderReserva`; `respuesta` es `ACEPTAR` o `RECHAZAR`. */
@Serializable
data class ResponderReservaRequestDto(
    val reservaId: String,
    val respuesta: String,
)

/** `estado` es `CONFIRMADA` o `RECHAZADA`. */
@Serializable
data class ResponderReservaResultadoDto(val estado: String)
