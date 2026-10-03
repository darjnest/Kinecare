package com.darjnest.kinecare.core.common.domain.model

import kotlinx.datetime.Instant

enum class EstadoReserva {
    SOLICITADA,
    CONFIRMADA,
    EN_CURSO,
    COMPLETADA,
    CANCELADA_CLIENTE,
    CANCELADA_PROFESIONAL,
    RECHAZADA,
}

/** Respuesta del profesional a una reserva en [EstadoReserva.SOLICITADA]. */
enum class RespuestaReserva {
    /** Pasa a [EstadoReserva.CONFIRMADA]. */
    ACEPTAR,

    /** Pasa a [EstadoReserva.RECHAZADA] y libera el horario. */
    RECHAZAR,
}

data class Reserva(
    val id: String,
    val clienteId: String,
    val profesionalId: String,
    val servicioId: String,
    val modalidad: ModalidadServicio,
    val fechaHora: Instant,
    val direccion: Direccion?,
    val estado: EstadoReserva,
    val pago: Pago,
    val comisionPorcentaje: Double,
)
