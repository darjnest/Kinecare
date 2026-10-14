package com.darjnest.kinecare.core.common.domain.model

import kotlinx.datetime.Instant

/** Revision de una certificacion subida por el profesional; la resuelve soporte desde la consola. */
enum class EstadoCertificacion { EN_REVISION, APROBADA, RECHAZADA }

/**
 * Certificacion, diploma o postitulo que el profesional sube para acreditar una especialidad
 * (`profesionales/{id}/certificaciones/{certificacionId}`, docs/DATA_MODEL.md). El archivo vive en
 * Storage y la app nunca lo vuelve a leer: solo ve este registro.
 */
data class Certificacion(
    val id: String,
    val nombre: String,
    val estado: EstadoCertificacion,
    val tipoContenido: String,
    val tamanoBytes: Long,
    /** `null` mientras el servidor aun no confirma la hora de creacion. */
    val creadoEn: Instant?,
    val motivoRechazo: String? = null,
)
