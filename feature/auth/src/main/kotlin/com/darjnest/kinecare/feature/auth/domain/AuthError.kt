package com.darjnest.kinecare.feature.auth.domain

import com.darjnest.kinecare.core.common.result.Error

enum class AuthError : Error {
    CREDENCIALES_INVALIDAS,
    USUARIO_NO_ENCONTRADO,
    EMAIL_YA_REGISTRADO,
    SIN_INTERNET,
    DESCONOCIDO,
}
