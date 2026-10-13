package com.darjnest.kinecare.feature.verification.domain

import com.darjnest.kinecare.core.common.result.Error

/**
 * Errores de [com.darjnest.kinecare.feature.verification.data.repository.VerificacionRepository.consultarEstado].
 * Salvo [SIN_INTERNET] y [DESCONOCIDO], 1:1 con los `details.motivo` de `estadoVerificacion`.
 */
enum class EstadoVerificacionError : Error {
    SIN_SESION,
    SIN_INTERNET,

    /** La solicitud no existe o no es del usuario. */
    SOLICITUD_NO_ENCONTRADA,
    DESCONOCIDO,
}
