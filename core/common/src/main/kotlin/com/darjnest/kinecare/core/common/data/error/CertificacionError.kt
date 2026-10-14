package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/** Errores de [com.darjnest.kinecare.core.common.data.repository.CertificacionRepository]. */
enum class CertificacionError : Error {
    SIN_INTERNET,

    /** No hay sesion, o las reglas rechazaron la escritura (no es el dueno). */
    SIN_PERMISO,

    /** El archivo elegido no se pudo leer. */
    ARCHIVO_INVALIDO,

    /** Solo se aceptan PDF, JPEG y PNG. */
    TIPO_NO_PERMITIDO,

    /** Supera el limite de tamano (10 MB). */
    ARCHIVO_MUY_GRANDE,

    /** Storage no esta disponible en este ambiente (produccion sin bucket mientras siga en Spark). */
    ALMACENAMIENTO_NO_DISPONIBLE,

    DESCONOCIDO,
}
