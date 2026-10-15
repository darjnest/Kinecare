package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/** Errores de [com.darjnest.kinecare.core.common.data.repository.ComisionRepository]. */
enum class ComisionError : Error {
    SIN_INTERNET,
    SIN_PERMISO,
    DESCONOCIDO,
}
