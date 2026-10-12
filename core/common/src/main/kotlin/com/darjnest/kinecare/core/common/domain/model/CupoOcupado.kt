package com.darjnest.kinecare.core.common.domain.model

import kotlinx.datetime.Instant

/**
 * Tramo de la agenda de un profesional ya tomado por una reserva activa
 * (`SOLICITADA` o `CONFIRMADA`). Lo entrega la Cloud Function
 * `obtenerCuposOcupados` sin revelar de quien es la reserva: un cliente no
 * puede leer reservas ajenas por Security Rules.
 */
data class CupoOcupado(
    val inicio: Instant,
    val fin: Instant,
)
