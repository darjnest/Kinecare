package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/**
 * Errores de [com.darjnest.kinecare.core.common.data.repository.ReservaRepository.completar].
 * Salvo [SIN_INTERNET] y [DESCONOCIDO], cada valor corresponde 1:1 a un
 * `details.motivo` de la Cloud Function `completarReserva` (docs/DATA_MODEL.md).
 */
enum class CompletarReservaError : Error {
    SIN_SESION,
    SIN_INTERNET,

    /** El usuario autenticado no es Profesional. */
    ROL_INVALIDO,
    DATOS_INVALIDOS,

    /** La reserva no existe o es de otro profesional. */
    RESERVA_NO_ENCONTRADA,

    /** La reserva no esta `CONFIRMADA` (ya completada, cancelada, rechazada o sin responder). */
    RESERVA_NO_COMPLETABLE,

    /** La hora de la atencion todavia no llega. */
    CITA_NO_INICIADA,
    DESCONOCIDO,
}
