package com.darjnest.kinecare.feature.verification.domain

import com.darjnest.kinecare.core.common.result.Error

/**
 * Errores de [com.darjnest.kinecare.feature.verification.data.repository.VerificacionRepository.solicitar].
 * Salvo [SIN_INTERNET] y [DESCONOCIDO], cada valor corresponde 1:1 a un
 * `details.motivo` de la Cloud Function `solicitarVerificacion`.
 */
enum class SolicitarVerificacionError : Error {
    SIN_SESION,
    SIN_INTERNET,

    /** El usuario autenticado no es Profesional. */
    NO_ES_PROFESIONAL,

    /** El tipo de verificacion pedido no esta soportado (hoy solo `IDENTIDAD`). */
    TIPO_NO_SOPORTADO,

    /** El profesional no tiene perfil. */
    PERFIL_NO_ENCONTRADO,

    /** La identidad ya esta verificada: no es un fallo, la pantalla refresca el estado. */
    YA_VERIFICADO,

    /** El proveedor de verificacion no esta configurado en el backend. */
    PROVEEDOR_NO_CONFIGURADO,

    /** El proveedor de verificacion no respondio. */
    PROVEEDOR_NO_DISPONIBLE,
    DESCONOCIDO,
}
