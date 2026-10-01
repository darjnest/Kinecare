package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/**
 * Errores de [com.darjnest.kinecare.core.common.data.repository.ReservaRepository.crear].
 * Salvo [SIN_INTERNET] y [DESCONOCIDO], cada valor corresponde 1:1 a un
 * `details.motivo` de la Cloud Function `crearReserva` (docs/DATA_MODEL.md).
 * Separado de [ReservaError] para no ampliar los `when` de las lecturas.
 */
enum class CrearReservaError : Error {
    SIN_SESION,
    SIN_INTERNET,

    /** El usuario autenticado no es Cliente, o intenta reservarse a si mismo. */
    ROL_INVALIDO,
    DATOS_INVALIDOS,
    DIRECCION_REQUERIDA,

    /** El servicio no existe o esta pausado. */
    SERVICIO_NO_DISPONIBLE,

    /** Menos de 60 minutos de anticipacion, o mas de 60 dias. */
    ANTICIPACION_INSUFICIENTE,
    FUERA_DE_HORARIO,

    /** Otra reserva activa del profesional se superpone con el horario. */
    HORARIO_OCUPADO,
    DESCONOCIDO,
}
