package com.darjnest.kinecare.core.common.domain.model

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Lo que pide abrir una notificacion push al tocarla: de que [tipo] es y, si el backend lo mando, a que
 * [reservaId] se refiere (hoy solo informativa: las pantallas destino son listados). El destino
 * depende del rol de quien la abre: ver [destinoPara].
 *
 * Llega de dos formas, y ambas se resuelven aqui para que la app las trate igual: el `data` del
 * mensaje FCM (`tipo`, `reservaId`; es lo que recibe `MainActivity` cuando el sistema muestra la
 * notificacion con la app cerrada o en segundo plano) o el enlace interno
 * `kinecare://notificacion?tipo=...` que arma la propia app cuando muestra la notificacion con la
 * app abierta.
 */
data class AperturaNotificacion(
    val tipo: TipoNotificacion,
    val reservaId: String? = null,
) {
    /** Enlace interno; solo lo usa la app dentro de un `PendingIntent` explicito (no se expone a otras apps). */
    val enlace: String
        get() = buildString {
            append(ESQUEMA).append("://").append(HOST).append("?")
            append(DestinoNotificacion.CLAVE_TIPO).append("=").append(tipo.name)
            reservaId?.let {
                append("&").append(DestinoNotificacion.CLAVE_RESERVA_ID).append("=").append(URLEncoder.encode(it, "UTF-8"))
            }
        }

    /** La pantalla que abre para un usuario con [rol], o `null` si la notificacion no es para ese rol. */
    fun destinoPara(rol: RolUsuario): DestinoNotificacion? =
        if (rol in tipo.destinatarios) DestinoNotificacion.para(rol) else null

    /**
     * Que hacer con esta apertura segun el estado de la sesion. La notificacion se puede tocar con la
     * app cerrada: hay que esperar a que se restaure la sesion y a que la app salga de la pantalla de
     * acceso (que reenvia al inicio del rol) antes de navegar, y descartarla si no hay sesion o no es
     * para el rol de la cuenta (p. ej. un aviso de Profesional tocado con una cuenta Cliente).
     */
    fun resolver(
        rolUsuario: RolUsuario?,
        verificandoSesion: Boolean,
        enPantallaDeAcceso: Boolean,
    ): ResolucionApertura = when {
        rolUsuario == null -> if (verificandoSesion) ResolucionApertura.ESPERAR else ResolucionApertura.DESCARTAR
        enPantallaDeAcceso -> ResolucionApertura.ESPERAR
        destinoPara(rolUsuario) != null -> ResolucionApertura.ABRIR
        else -> ResolucionApertura.DESCARTAR
    }

    companion object {
        const val ESQUEMA = "kinecare"
        const val HOST = "notificacion"

        /** A partir del `data` del mensaje FCM; `null` si el `tipo` no se conoce. */
        fun desdeDatos(tipo: String?, reservaId: String?): AperturaNotificacion? {
            val tipoConocido = TipoNotificacion.desdeTexto(tipo) ?: return null
            return AperturaNotificacion(tipoConocido, reservaId?.trim()?.takeIf { it.isNotEmpty() })
        }

        /** A partir de un enlace interno; `null` si no es de `kinecare://notificacion` o esta mal formado. */
        fun desdeEnlace(enlace: String?): AperturaNotificacion? {
            if (enlace.isNullOrBlank()) return null
            val uri = try {
                URI(enlace)
            } catch (_: Exception) {
                return null
            }
            if (uri.scheme != ESQUEMA || uri.host != HOST) return null
            val parametros = uri.rawQuery
                ?.split("&")
                ?.map { it.split("=", limit = 2) }
                ?.filter { it.size == 2 }
                ?.associate { it[0] to URLDecoder.decode(it[1], "UTF-8") }
                .orEmpty()
            return desdeDatos(parametros[DestinoNotificacion.CLAVE_TIPO], parametros[DestinoNotificacion.CLAVE_RESERVA_ID])
        }
    }
}

/** Decision de [AperturaNotificacion.resolver]. */
enum class ResolucionApertura { ESPERAR, ABRIR, DESCARTAR }
