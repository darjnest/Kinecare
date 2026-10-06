package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/**
 * Errores de [com.darjnest.kinecare.core.common.data.repository.PagoRepository.obtenerUrlConexion].
 * Salvo [SIN_INTERNET] y [DESCONOCIDO], 1:1 con los `details.motivo` de `conectarMercadoPago`.
 */
enum class ConectarMercadoPagoError : Error {
    SIN_SESION,
    SIN_INTERNET,

    /** El usuario autenticado no es Profesional. */
    ROL_INVALIDO,
    DESCONOCIDO,
}
