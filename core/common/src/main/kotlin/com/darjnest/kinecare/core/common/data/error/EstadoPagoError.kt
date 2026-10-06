package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/**
 * Errores de [com.darjnest.kinecare.core.common.data.repository.PagoRepository.consultarEstado].
 * Salvo [SIN_INTERNET] y [DESCONOCIDO], 1:1 con los `details.motivo` de `estadoPago`.
 */
enum class EstadoPagoError : Error {
    SIN_SESION,
    SIN_INTERNET,
    DATOS_INVALIDOS,

    /** El pago no existe o no es del usuario. */
    PAGO_NO_ENCONTRADO,
    PROFESIONAL_SIN_CUENTA_MP,
    PASARELA_NO_DISPONIBLE,
    DESCONOCIDO,
}
