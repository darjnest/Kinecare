package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/** Errores de [com.darjnest.kinecare.core.common.data.repository.UsuarioRepository]. */
enum class UsuarioError : Error {
    SIN_INTERNET,
    NO_ENCONTRADO,
    DESCONOCIDO,
}
