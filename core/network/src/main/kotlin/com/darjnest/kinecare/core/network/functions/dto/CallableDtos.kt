package com.darjnest.kinecare.core.network.functions.dto

import kotlinx.serialization.Serializable

/**
 * Sobre del protocolo HTTP de las funciones `onCall` de Firebase: la
 * peticion va en `{"data": ...}`, el exito vuelve en `{"result": ...}` y el
 * error en `{"error": {"status", "message", "details"}}` con un HTTP no-2xx.
 */
@Serializable
data class CallableRequest<T>(val data: T)

@Serializable
data class CallableResponse<T>(val result: T)

@Serializable
data class CallableErrorBody(val error: CallableErrorDto)

@Serializable
data class CallableErrorDto(
    /** Codigo canonico en mayusculas: `UNAUTHENTICATED`, `ALREADY_EXISTS`, etc. */
    val status: String? = null,
    val message: String? = null,
    val details: CallableErrorDetailsDto? = null,
)

/** `details` que KineCare adjunta a cada `HttpsError` (ver docs/DATA_MODEL.md). */
@Serializable
data class CallableErrorDetailsDto(val motivo: String? = null)
