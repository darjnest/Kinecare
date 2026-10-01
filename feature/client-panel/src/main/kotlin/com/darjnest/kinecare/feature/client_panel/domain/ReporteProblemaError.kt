package com.darjnest.kinecare.feature.client_panel.domain

import com.darjnest.kinecare.core.common.result.Error

/** Errores de [com.darjnest.kinecare.feature.client_panel.data.repository.ReporteProblemaRepository]. */
enum class ReporteProblemaError : Error {
    SIN_INTERNET,
    SIN_PERMISO,
    DESCONOCIDO,
}
