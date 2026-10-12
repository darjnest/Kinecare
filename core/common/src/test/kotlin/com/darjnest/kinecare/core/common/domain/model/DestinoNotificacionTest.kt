package com.darjnest.kinecare.core.common.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DestinoNotificacionTest {

    @Test
    fun `los tipos que manda el backend se reconocen`() {
        listOf(
            "NUEVA_SOLICITUD",
            "RESERVA_CONFIRMADA",
            "RESERVA_RECHAZADA",
            "SOLICITUD_EXPIRADA",
            "RESERVA_CANCELADA",
            "DEJAR_RESENA",
        ).forEach { texto ->
            assertEquals(texto, TipoNotificacion.desdeTexto(texto)?.name, texto)
        }
    }

    @Test
    fun `el tipo se compara sin importar mayusculas ni espacios`() {
        assertEquals(TipoNotificacion.RESERVA_CONFIRMADA, TipoNotificacion.desdeTexto("  reserva_confirmada "))
    }

    @Test
    fun `un tipo desconocido o ausente no se reconoce`() {
        assertNull(TipoNotificacion.desdeTexto("PROMOCION"))
        assertNull(TipoNotificacion.desdeTexto(""))
        assertNull(TipoNotificacion.desdeTexto(null))
    }

    @Test
    fun `cada tipo es para el rol al que el backend se lo envia`() {
        assertEquals(setOf(RolUsuario.PROFESIONAL), TipoNotificacion.NUEVA_SOLICITUD.destinatarios)
        assertEquals(setOf(RolUsuario.CLIENTE), TipoNotificacion.RESERVA_CONFIRMADA.destinatarios)
        assertEquals(setOf(RolUsuario.CLIENTE), TipoNotificacion.DEJAR_RESENA.destinatarios)
        // La cancelacion llega a quien no la hizo: al profesional si cancela el cliente y al reves.
        assertEquals(setOf(RolUsuario.CLIENTE, RolUsuario.PROFESIONAL), TipoNotificacion.RESERVA_CANCELADA.destinatarios)
    }

    @Test
    fun `cada rol tiene su pantalla de reservas`() {
        assertEquals(DestinoNotificacion.MIS_CITAS, DestinoNotificacion.para(RolUsuario.CLIENTE))
        assertEquals(DestinoNotificacion.SOLICITUDES, DestinoNotificacion.para(RolUsuario.PROFESIONAL))
        assertEquals(RolUsuario.CLIENTE, DestinoNotificacion.MIS_CITAS.rol)
        assertEquals(RolUsuario.PROFESIONAL, DestinoNotificacion.SOLICITUDES.rol)
    }
}
