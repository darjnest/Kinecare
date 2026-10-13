package com.darjnest.kinecare.core.network.firebase.storage

import com.darjnest.kinecare.core.common.domain.model.RolUsuario

/** Lado mayor maximo (px) de la foto de perfil que se sube; una foto de camara pesa mucho mas de lo que un avatar necesita. */
internal const val LADO_MAXIMO_PX = 1280

/** Storage rechaza archivos de 5 MB o mas (`storage.rules`); se deja margen. */
internal const val TAMANO_MAXIMO_BYTES = 4 * 1024 * 1024

/** Calidades JPEG que se prueban de mayor a menor hasta que el archivo cabe bajo el limite. */
internal val CALIDADES_JPEG = listOf(85, 70, 55)

/**
 * Ruta de la foto de perfil en Storage (docs/DATA_MODEL.md#firebase-storage). Una sola ruta fija por
 * usuario: reemplazar es sobreescribir (las reglas no permiten borrar).
 */
internal fun rutaFotoPerfil(usuarioId: String, rol: RolUsuario): String = when (rol) {
    RolUsuario.PROFESIONAL -> "profesionales/$usuarioId/foto-perfil.jpg"
    RolUsuario.CLIENTE -> "clientes/$usuarioId/foto-perfil.jpg"
}

/**
 * `inSampleSize` (potencia de 2) para decodificar la imagen sin cargarla entera en memoria: el
 * mayor que deja el lado mayor decodificado en [lado] px o mas, de modo que el escalado final
 * siempre reduce (nunca agranda).
 */
internal fun calcularInSampleSize(ancho: Int, alto: Int, lado: Int = LADO_MAXIMO_PX): Int {
    var muestra = 1
    val mayor = maxOf(ancho, alto)
    while (mayor / (muestra * 2) >= lado) muestra *= 2
    return muestra
}

/** Dimensiones finales: el lado mayor queda en [lado] px conservando la proporcion; una imagen menor no se agranda. */
internal fun dimensionesReducidas(ancho: Int, alto: Int, lado: Int = LADO_MAXIMO_PX): Pair<Int, Int> {
    val mayor = maxOf(ancho, alto)
    if (mayor <= lado) return ancho to alto
    val factor = lado.toDouble() / mayor
    return maxOf(1, Math.round(ancho * factor).toInt()) to maxOf(1, Math.round(alto * factor).toInt())
}

/**
 * Agrega `v=<version>` a la URL de descarga. La ruta de Storage no cambia al reemplazar la foto, asi
 * que sin esto Coil (que cachea por URL) seguiria mostrando la foto anterior.
 */
internal fun conVersion(url: String, version: Long): String {
    val separador = if ('?' in url) '&' else '?'
    return "$url${separador}v=$version"
}
