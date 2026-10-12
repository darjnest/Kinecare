package com.darjnest.kinecare.core.network.functions.dto

import kotlinx.serialization.Serializable

/** `data` de `completarReserva`. */
@Serializable
data class CompletarReservaRequestDto(val reservaId: String)

/** `estado` es siempre `COMPLETADA`. */
@Serializable
data class CompletarReservaResultadoDto(val estado: String)
