package com.darjnest.kinecare.core.common.data.error

import com.darjnest.kinecare.core.common.result.Error

/** Errores de [com.darjnest.kinecare.core.common.data.repository.FotoPerfilRepository]. */
enum class FotoPerfilError : Error {
    SIN_INTERNET,

    /** No hay sesion, o las reglas de Storage rechazaron la escritura (no es el dueno). */
    SIN_PERMISO,

    /** La imagen elegida no se pudo leer o no es una imagen valida. */
    IMAGEN_INVALIDA,

    /** Ni reduciendo la calidad cabe bajo el limite de Storage (5 MB). */
    IMAGEN_MUY_GRANDE,

    /**
     * Storage no esta disponible en este ambiente (el bucket de produccion no existe mientras el
     * proyecto siga en plan Spark): la app sigue funcionando, solo no se puede cambiar la foto.
     */
    ALMACENAMIENTO_NO_DISPONIBLE,

    DESCONOCIDO,
}
