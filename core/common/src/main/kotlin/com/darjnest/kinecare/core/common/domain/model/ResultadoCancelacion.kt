package com.darjnest.kinecare.core.common.domain.model

/** Que pasa con el dinero al cancelar una reserva (`reembolso` de `cancelarReserva`, docs/DATA_MODEL.md). */
enum class ReembolsoCancelacion {
    /** La reserva no tenia un pago autorizado: no hay nada que devolver. */
    NO_APLICA,

    /** Corresponde reembolso total (queda pendiente de procesar por el backend). */
    TOTAL,

    /** Pago autorizado pero la politica no da reembolso automatico (cliente con menos de 24 h). */
    NINGUNO,
}

/**
 * Resultado de cancelar una reserva: [estado] es `CANCELADA_CLIENTE` o
 * `CANCELADA_PROFESIONAL`; [reembolso] es `null` si el backend no lo informo.
 */
data class ResultadoCancelacion(
    val estado: EstadoReserva,
    val reembolso: ReembolsoCancelacion?,
)
