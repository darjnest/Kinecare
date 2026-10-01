package com.darjnest.kinecare.core.network.functions.dto

import kotlinx.serialization.Serializable

/** `data` de `crearReserva`; `fechaHora` es un instante ISO-8601 en UTC. */
@Serializable
data class CrearReservaRequestDto(
    val profesionalId: String,
    val servicioId: String,
    val fechaHora: String,
    val direccion: DireccionDto? = null,
)

@Serializable
data class DireccionDto(
    val calle: String,
    val numero: String,
    val comuna: String,
    val ciudad: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val indicaciones: String? = null,
)

@Serializable
data class CrearReservaResultadoDto(val reservaId: String)
