package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/** Errores de [com.darjnest.kinecare.core.common.data.repository.DispositivoPushRepository]. */
enum class DispositivoPushError : Error {
    /** No hay un usuario autenticado a quien asociar el token. */
    SIN_SESION,
    SIN_INTERNET,

    /** FCM no entrego un token (sin Google Play Services, sin red al pedirlo, etc.). */
    SIN_TOKEN,
    DESCONOCIDO,
}
