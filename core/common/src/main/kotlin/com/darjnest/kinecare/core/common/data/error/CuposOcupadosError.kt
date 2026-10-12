package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/** Errores de [com.darjnest.kinecare.core.common.data.repository.ReservaRepository.obtenerCuposOcupados]. */
enum class CuposOcupadosError : Error {
    SIN_SESION,
    SIN_INTERNET,
    DESCONOCIDO,
}
