package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/**
 * Errores de [com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository].
 * Movido desde `:feature:search` a `:core:common` (ver docs/ARCHITECTURE.md)
 * porque `:feature:client-panel` tambien necesita resolver un profesional
 * por id (favoritos) y las features nunca se dependen entre si.
 */
enum class ProfesionalError : Error {
    SIN_INTERNET,
    NO_ENCONTRADO,
    DESCONOCIDO,
}
