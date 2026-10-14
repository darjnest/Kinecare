package com.darjnest.kinecare.core.network.firebase.storage

import com.darjnest.kinecare.core.common.data.error.CertificacionError
import com.darjnest.kinecare.core.common.result.Result

/** Tamano maximo de una certificacion; Storage rechaza 10 MB o mas (`storage.rules`). */
internal const val TAMANO_MAXIMO_DOCUMENTO_BYTES = 10 * 1024 * 1024 - 1

/** Tipos que acepta `storage.rules`, con la extension con que se guarda cada uno. */
internal val EXTENSIONES_DOCUMENTO = mapOf(
    "application/pdf" to "pdf",
    "image/jpeg" to "jpg",
    "image/png" to "png",
)

/** Archivo ya leido en memoria, listo para subir. [nombreSugerido] es el nombre visible sin extension. */
class DocumentoLeido(
    val bytes: ByteArray,
    val tipoContenido: String,
    val nombreSugerido: String,
)

/** Lee el documento elegido por el usuario. Separado para poder probar el repositorio sin Android. */
interface LectorDocumento {
    /** @return los bytes y su tipo, o `ARCHIVO_INVALIDO`/`TIPO_NO_PERMITIDO`/`ARCHIVO_MUY_GRANDE`. */
    suspend fun leer(uri: String): Result<DocumentoLeido, CertificacionError>
}

/** Quita la extension de un nombre de archivo ("Diploma.pdf" -> "Diploma"). */
internal fun nombreSinExtension(nombreArchivo: String): String {
    val punto = nombreArchivo.lastIndexOf('.')
    return (if (punto > 0) nombreArchivo.substring(0, punto) else nombreArchivo).trim()
}
