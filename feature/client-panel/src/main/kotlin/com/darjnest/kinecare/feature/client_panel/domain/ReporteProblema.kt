@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.domain

import kotlinx.datetime.Instant

/**
 * Problema que un Cliente reporta sobre una de sus reservas
 * (`reportesProblema/{reservaId}`, ver docs/DATA_MODEL.md). Solo lo usa
 * `:feature:client-panel`, por eso vive aqui y no en `:core:common`.
 *
 * El id es siempre el [reservaId]: una reserva admite a lo mas un reporte.
 */
data class ReporteProblema(
    val reservaId: String,
    val clienteId: String,
    val profesionalId: String,
    val motivo: MotivoReporte,
    val descripcion: String,
    val estado: EstadoReporte,
    val fecha: Instant,
)

/** Debe coincidir con la lista de `motivo` permitidos en `firestore.rules`. */
enum class MotivoReporte {
    PROFESIONAL_NO_LLEGO,
    ATRASO,
    COBRO_INCORRECTO,
    CONDUCTA_INAPROPIADA,
    CALIDAD_ATENCION,
    OTRO,
}

/**
 * El cliente solo puede crear reportes `ABIERTO`; los demas estados los
 * escribe el equipo de soporte desde la consola (las reglas deniegan `update`).
 */
enum class EstadoReporte {
    ABIERTO,
    EN_REVISION,
    RESUELTO,
}
