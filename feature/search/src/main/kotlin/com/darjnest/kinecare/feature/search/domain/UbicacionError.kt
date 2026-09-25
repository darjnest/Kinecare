package com.darjnest.kinecare.feature.search.domain

import com.darjnest.kinecare.core.common.result.Error

enum class UbicacionError : Error {
    PERMISO_DENEGADO,
    UBICACION_NO_DISPONIBLE,
    DESCONOCIDO,
}
