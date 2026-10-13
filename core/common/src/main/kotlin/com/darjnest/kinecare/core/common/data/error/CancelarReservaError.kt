package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/**
 * Errores de [com.darjnest.kinecare.core.common.data.repository.ReservaRepository.cancelar].
 * Salvo [SIN_INTERNET] y [DESCONOCIDO], cada valor corresponde 1:1 a un
 * `details.motivo` de la Cloud Function `cancelarReserva` (docs/DATA_MODEL.md).
 */
enum class CancelarReservaError : Error {
    SIN_SESION,
    SIN_INTERNET,
    DATOS_INVALIDOS,

    /** El usuario autenticado no es ni el cliente ni el profesional de la reserva. */
    NO_AUTORIZADO,

    /** La reserva no existe. */
    RESERVA_NO_ENCONTRADA,

    /** La reserva ya no esta `SOLICITADA` ni `CONFIRMADA` (completada, cancelada, rechazada...). */
    ESTADO_INVALIDO,

    /** La hora de la cita ya llego: no se puede cancelar. */
    CITA_YA_INICIADA,

    /** El motivo supera el largo permitido. */
    MOTIVO_INVALIDO,
    DESCONOCIDO,
}
