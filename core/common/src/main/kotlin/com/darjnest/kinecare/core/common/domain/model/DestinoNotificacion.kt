package com.darjnest.kinecare.core.common.domain.model

/**
 * Valor de `data.tipo` de las notificaciones push de reservas que manda el backend (trigger
 * `notificarReserva`, `functions/src/notificaciones.ts`). Un `tipo` que la app no conoce (el backend
 * puede agregar otros antes que una version nueva de la app) da `null` en [desdeTexto]: la
 * notificacion igual se muestra, solo que al tocarla abre la app donde estaba.
 *
 * [destinatarios] son los roles a los que el backend se lo envia; `RESERVA_CANCELADA` va a ambos
 * (al profesional si cancela el cliente y al reves), por eso el destino de la pantalla depende del rol.
 */
enum class TipoNotificacion(val destinatarios: Set<RolUsuario>) {
    NUEVA_SOLICITUD(setOf(RolUsuario.PROFESIONAL)),
    RESERVA_CONFIRMADA(setOf(RolUsuario.CLIENTE)),
    RESERVA_RECHAZADA(setOf(RolUsuario.CLIENTE)),
    SOLICITUD_EXPIRADA(setOf(RolUsuario.CLIENTE)),
    DEJAR_RESENA(setOf(RolUsuario.CLIENTE)),
    RESERVA_CANCELADA(setOf(RolUsuario.CLIENTE, RolUsuario.PROFESIONAL)),
    ;

    companion object {
        fun desdeTexto(texto: String?): TipoNotificacion? =
            texto?.trim()?.uppercase()?.let { valor -> entries.firstOrNull { it.name == valor } }
    }
}

/** Pantalla a la que lleva tocar una notificacion push: la lista de reservas de cada rol. */
enum class DestinoNotificacion(val rol: RolUsuario) {
    /** Mis Citas del Cliente. */
    MIS_CITAS(RolUsuario.CLIENTE),

    /** Solicitudes de atencion del Profesional. */
    SOLICITUDES(RolUsuario.PROFESIONAL),
    ;

    companion object {
        const val CLAVE_TIPO = "tipo"
        const val CLAVE_RESERVA_ID = "reservaId"

        fun para(rol: RolUsuario): DestinoNotificacion = when (rol) {
            RolUsuario.CLIENTE -> MIS_CITAS
            RolUsuario.PROFESIONAL -> SOLICITUDES
        }
    }
}
