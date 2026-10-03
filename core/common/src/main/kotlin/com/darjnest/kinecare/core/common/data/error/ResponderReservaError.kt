package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/**
 * Errores de [com.darjnest.kinecare.core.common.data.repository.ReservaRepository.responder].
 * Salvo [SIN_INTERNET] y [DESCONOCIDO], cada valor corresponde 1:1 a un
 * `details.motivo` de la Cloud Function `responderReserva` (docs/DATA_MODEL.md).
 */
enum class ResponderReservaError : Error {
    SIN_SESION,
    SIN_INTERNET,

    /** El usuario autenticado no es Profesional. */
    ROL_INVALIDO,
    DATOS_INVALIDOS,

    /** La reserva no existe o es de otro profesional. */
    RESERVA_NO_ENCONTRADA,

    /** La reserva ya no esta en `SOLICITADA` (otra respuesta o cancelacion llego antes). */
    RESERVA_YA_RESPONDIDA,

    /** Se intento aceptar una solicitud cuya hora ya paso. */
    RESERVA_VENCIDA,
    DESCONOCIDO,
}
