package com.darjnest.kinecare.core.common.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AperturaNotificacionTest {

    private val confirmada = AperturaNotificacion(TipoNotificacion.RESERVA_CONFIRMADA, "res-1")
    private val nuevaSolicitud = AperturaNotificacion(TipoNotificacion.NUEVA_SOLICITUD, "res-1")
    private val cancelada = AperturaNotificacion(TipoNotificacion.RESERVA_CANCELADA, "res-1")

    @Test
    fun `el data de FCM con un tipo conocido arma la apertura con la reserva`() {
        assertEquals(confirmada, AperturaNotificacion.desdeDatos("RESERVA_CONFIRMADA", "res-1"))
    }

    @Test
    fun `una solicitud nueva sin reservaId igual se reconoce`() {
        assertEquals(AperturaNotificacion(TipoNotificacion.NUEVA_SOLICITUD, null), AperturaNotificacion.desdeDatos("NUEVA_SOLICITUD", null))
    }

    @Test
    fun `un reservaId en blanco se descarta`() {
        assertNull(AperturaNotificacion.desdeDatos("NUEVA_SOLICITUD", "  ")?.reservaId)
    }

    @Test
    fun `un tipo desconocido no abre nada`() {
        assertNull(AperturaNotificacion.desdeDatos("PROMOCION", "res-1"))
        assertNull(AperturaNotificacion.desdeDatos(null, null))
    }

    @Test
    fun `el enlace se arma con el tipo y la reserva`() {
        assertEquals("kinecare://notificacion?tipo=RESERVA_CONFIRMADA&reservaId=res-1", confirmada.enlace)
        assertEquals("kinecare://notificacion?tipo=NUEVA_SOLICITUD", AperturaNotificacion(TipoNotificacion.NUEVA_SOLICITUD).enlace)
    }

    @Test
    fun `el enlace se puede volver a leer y conserva el tipo y la reserva`() {
        listOf(
            confirmada,
            AperturaNotificacion(TipoNotificacion.DEJAR_RESENA, "a b&c=d/ñ"),
            AperturaNotificacion(TipoNotificacion.RESERVA_CANCELADA),
        ).forEach { original ->
            assertEquals(original, AperturaNotificacion.desdeEnlace(original.enlace), original.enlace)
        }
    }

    @Test
    fun `los enlaces ajenos o mal formados se ignoran`() {
        assertNull(AperturaNotificacion.desdeEnlace(null))
        assertNull(AperturaNotificacion.desdeEnlace(""))
        assertNull(AperturaNotificacion.desdeEnlace("https://notificacion?tipo=NUEVA_SOLICITUD"))
        assertNull(AperturaNotificacion.desdeEnlace("kinecare://pago/resultado?pagoId=1"))
        assertNull(AperturaNotificacion.desdeEnlace("kinecare://mp/conectado"))
        assertNull(AperturaNotificacion.desdeEnlace("kinecare://notificacion"))
        assertNull(AperturaNotificacion.desdeEnlace("kinecare://notificacion?tipo=PROMOCION"))
        assertNull(AperturaNotificacion.desdeEnlace("kinecare://notificacion con espacios"))
    }

    @Test
    fun `el destino depende del rol de quien la abre`() {
        assertEquals(DestinoNotificacion.MIS_CITAS, confirmada.destinoPara(RolUsuario.CLIENTE))
        assertEquals(DestinoNotificacion.SOLICITUDES, nuevaSolicitud.destinoPara(RolUsuario.PROFESIONAL))
        // La cancelacion llega a los dos roles y cada uno abre su lista.
        assertEquals(DestinoNotificacion.MIS_CITAS, cancelada.destinoPara(RolUsuario.CLIENTE))
        assertEquals(DestinoNotificacion.SOLICITUDES, cancelada.destinoPara(RolUsuario.PROFESIONAL))
    }

    @Test
    fun `un aviso del otro rol no tiene destino`() {
        assertNull(nuevaSolicitud.destinoPara(RolUsuario.CLIENTE))
        assertNull(confirmada.destinoPara(RolUsuario.PROFESIONAL))
    }

    @Test
    fun `con la app cerrada espera a que se restaure la sesion`() {
        assertEquals(ResolucionApertura.ESPERAR, confirmada.resolver(null, verificandoSesion = true, enPantallaDeAcceso = true))
    }

    @Test
    fun `sin sesion se descarta para no abrirla despues de un login ajeno`() {
        assertEquals(ResolucionApertura.DESCARTAR, confirmada.resolver(null, verificandoSesion = false, enPantallaDeAcceso = true))
    }

    @Test
    fun `con sesion pero aun en la pantalla de acceso espera a que navegue al inicio`() {
        assertEquals(
            ResolucionApertura.ESPERAR,
            confirmada.resolver(RolUsuario.CLIENTE, verificandoSesion = false, enPantallaDeAcceso = true),
        )
    }

    @Test
    fun `el cliente y el profesional abren la lista de su rol`() {
        assertEquals(
            ResolucionApertura.ABRIR,
            confirmada.resolver(RolUsuario.CLIENTE, verificandoSesion = false, enPantallaDeAcceso = false),
        )
        assertEquals(
            ResolucionApertura.ABRIR,
            nuevaSolicitud.resolver(RolUsuario.PROFESIONAL, verificandoSesion = false, enPantallaDeAcceso = false),
        )
        assertEquals(
            ResolucionApertura.ABRIR,
            cancelada.resolver(RolUsuario.PROFESIONAL, verificandoSesion = false, enPantallaDeAcceso = false),
        )
    }

    @Test
    fun `una notificacion de otro rol se descarta`() {
        assertEquals(
            ResolucionApertura.DESCARTAR,
            nuevaSolicitud.resolver(RolUsuario.CLIENTE, verificandoSesion = false, enPantallaDeAcceso = false),
        )
        assertEquals(
            ResolucionApertura.DESCARTAR,
            confirmada.resolver(RolUsuario.PROFESIONAL, verificandoSesion = false, enPantallaDeAcceso = false),
        )
    }
}
