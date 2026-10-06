package com.darjnest.kinecare.core.network.functions.dto

import kotlinx.serialization.Serializable

/**
 * `data` de `iniciarConexionMercadoPago` y `desconectarMercadoPago`: ninguna
 * de las dos recibe parametros (el profesional sale de `request.auth`), asi
 * que se serializa como `{}`.
 */
@Serializable
object MercadoPagoSinDatosDto

/** Resultado de `iniciarConexionMercadoPago`: URL de autorizacion OAuth de Mercado Pago. */
@Serializable
data class IniciarConexionMercadoPagoResultadoDto(val urlAutorizacion: String)

/** Resultado de `desconectarMercadoPago`. */
@Serializable
data class DesconectarMercadoPagoResultadoDto(val desconectado: Boolean)
