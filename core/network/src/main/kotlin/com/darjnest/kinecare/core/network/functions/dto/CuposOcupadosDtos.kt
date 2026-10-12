package com.darjnest.kinecare.core.network.functions.dto

import kotlinx.serialization.Serializable

/** `data` de `obtenerCuposOcupados`; `desde` y `hasta` son instantes ISO-8601 (ventana de 15 dias como maximo). */
@Serializable
data class CuposOcupadosRequestDto(
    val profesionalId: String,
    val desde: String,
    val hasta: String,
)

/** Un tramo ocupado; `inicio` y `fin` son instantes ISO-8601. */
@Serializable
data class CupoOcupadoDto(
    val inicio: String,
    val fin: String,
)

/** `result` de `obtenerCuposOcupados`: `{"cupos": [...]}`. */
@Serializable
data class CuposOcupadosResultadoDto(val cupos: List<CupoOcupadoDto> = emptyList())
