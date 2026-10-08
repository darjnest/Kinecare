package com.darjnest.kinecare.core.network.functions.dto

import kotlinx.serialization.Serializable

/** `data` de `solicitarVerificacion`; hoy solo se soporta `tipo = "IDENTIDAD"` (`TipoInsignia`). */
@Serializable
data class SolicitarVerificacionRequestDto(val tipo: String)

/**
 * `url` es la pagina de verificacion hospedada por el proveedor (https, Didit);
 * `estado` llega como `PENDIENTE` y el cliente no lo usa: el estado se
 * consulta siempre con `estadoVerificacion`.
 */
@Serializable
data class SolicitarVerificacionResultadoDto(
    val solicitudId: String,
    val url: String,
    val estado: String? = null,
)

/** Sin `solicitudId` el backend responde por la verificacion de identidad mas reciente de quien llama. */
@Serializable
data class EstadoVerificacionRequestDto(val solicitudId: String? = null)

/**
 * `estado` es `NO_SOLICITADO`, `PENDIENTE`, `APROBADO` o `RECHAZADO`; `motivo`
 * solo acompana a `RECHAZADO` (`DECLINED`, `RUT_NO_COINCIDE`, `EXPIRADA`,
 * `ABANDONADA`, `KYC_VENCIDO`).
 */
@Serializable
data class EstadoVerificacionResultadoDto(
    val estado: String,
    val solicitudId: String? = null,
    val motivo: String? = null,
)
