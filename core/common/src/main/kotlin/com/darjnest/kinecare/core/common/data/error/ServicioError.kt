package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/** Errores de [com.darjnest.kinecare.core.common.data.repository.ServicioRepository]. */
enum class ServicioError : Error {
    SIN_INTERNET,
    DESCONOCIDO,
}
