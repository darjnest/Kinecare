package com.darjnest.kinecare.feature.verification.presentation.util

import com.darjnest.kinecare.feature.verification.domain.EstadoVerificacionError
import com.darjnest.kinecare.feature.verification.domain.MotivoRechazoVerificacion
import com.darjnest.kinecare.feature.verification.domain.SolicitarVerificacionError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MensajesVerificacionTest {

    @Test
    fun `cada SolicitarVerificacionError tiene un mensaje propio`() {
        val mensajes = SolicitarVerificacionError.entries.map(::mensajeErrorSolicitar)

        assertTrue(mensajes.all { it.isNotBlank() })
        assertEquals(mensajes.size, mensajes.toSet().size)
    }

    @Test
    fun `cada EstadoVerificacionError tiene un mensaje propio`() {
        val mensajes = EstadoVerificacionError.entries.map(::mensajeErrorEstado)

        assertTrue(mensajes.all { it.isNotBlank() })
        assertEquals(mensajes.size, mensajes.toSet().size)
    }

    @Test
    fun `los titulos de rechazo siguen el texto acordado por motivo`() {
        assertEquals(
            "El documento no coincide con el RUT de tu cuenta",
            tituloRechazo(MotivoRechazoVerificacion.RUT_NO_COINCIDE),
        )
        assertEquals("No pudimos verificar tu identidad", tituloRechazo(MotivoRechazoVerificacion.DECLINED))
        assertEquals("No completaste la verificación", tituloRechazo(MotivoRechazoVerificacion.EXPIRADA))
        assertEquals("No completaste la verificación", tituloRechazo(MotivoRechazoVerificacion.ABANDONADA))
        assertEquals("Tu verificación venció", tituloRechazo(MotivoRechazoVerificacion.KYC_VENCIDO))
    }

    @Test
    fun `un rechazo sin motivo conocido usa el mensaje generico de DECLINED`() {
        assertEquals(tituloRechazo(MotivoRechazoVerificacion.DECLINED), tituloRechazo(null))
        assertEquals(mensajeRechazo(MotivoRechazoVerificacion.DECLINED), mensajeRechazo(null))
    }

    @Test
    fun `cada motivo de rechazo explica que hacer`() {
        for (motivo in MotivoRechazoVerificacion.entries) {
            assertTrue(mensajeRechazo(motivo).isNotBlank(), "motivo $motivo")
        }
        assertNotEquals(
            mensajeRechazo(MotivoRechazoVerificacion.EXPIRADA),
            mensajeRechazo(MotivoRechazoVerificacion.ABANDONADA),
        )
    }

    @Test
    fun `solo se reintentan los errores de red y desconocidos`() {
        assertTrue(esReintentable(EstadoVerificacionError.SIN_INTERNET))
        assertTrue(esReintentable(EstadoVerificacionError.DESCONOCIDO))
        assertFalse(esReintentable(EstadoVerificacionError.SIN_SESION))
        assertFalse(esReintentable(EstadoVerificacionError.SOLICITUD_NO_ENCONTRADA))
    }
}
