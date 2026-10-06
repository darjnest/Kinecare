package com.darjnest.kinecare.core.network.functions.dto

import kotlinx.serialization.Serializable

/** `data` de `iniciarPago`: solo la reserva; monto, comision y vendedor salen del servidor. */
@Serializable
data class IniciarPagoRequestDto(val reservaId: String)

@Serializable
data class IniciarPagoResultadoDto(
    val pagoId: String,
    val initPoint: String,
)

@Serializable
data class EstadoPagoRequestDto(val pagoId: String)

/** `estado` es `PENDIENTE`, `AUTORIZADO`, `RECHAZADO` o `REEMBOLSADO`. */
@Serializable
data class EstadoPagoResultadoDto(val estado: String)

/** `conectarMercadoPago` no recibe parametros: el profesional sale de la sesion. */
@Serializable
class ConectarMercadoPagoRequestDto

@Serializable
data class ConectarMercadoPagoResultadoDto(val authorizationUrl: String)
