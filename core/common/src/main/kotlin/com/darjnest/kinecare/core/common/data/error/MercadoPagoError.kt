package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/**
 * Errores de [com.darjnest.kinecare.core.common.data.repository.MercadoPagoRepository].
 * Salvo [SIN_INTERNET] y [DESCONOCIDO], cada valor corresponde 1:1 a un
 * `details.motivo` de las Cloud Functions `iniciarConexionMercadoPago` y
 * `desconectarMercadoPago`.
 */
enum class MercadoPagoError : Error {
    SIN_SESION,
    SIN_INTERNET,

    /** El usuario autenticado no es Profesional. */
    ROL_INVALIDO,
    DESCONOCIDO,
}
