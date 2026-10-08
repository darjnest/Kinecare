package com.darjnest.kinecare.feature.verification.domain

/**
 * Verificacion abierta por la Cloud Function `solicitarVerificacion`:
 * [solicitudId] es el documento `solicitudesVerificacion/{id}` y [url] la
 * pagina hospedada por el proveedor (Didit) donde el profesional sube su
 * cedula y se hace la selfie con prueba de vida. Es idempotente: mientras haya
 * una verificacion abierta devuelve la misma [url].
 *
 * No es `data class` a proposito: asi un `toString` accidental (log, mensaje de
 * un test) no filtra la URL, que es sensible.
 */
class IntentoVerificacion(
    val solicitudId: String,
    val url: String,
) {
    override fun equals(other: Any?): Boolean =
        other is IntentoVerificacion && other.solicitudId == solicitudId && other.url == url

    override fun hashCode(): Int = 31 * solicitudId.hashCode() + url.hashCode()

    override fun toString(): String = "IntentoVerificacion(solicitudId=$solicitudId)"
}
