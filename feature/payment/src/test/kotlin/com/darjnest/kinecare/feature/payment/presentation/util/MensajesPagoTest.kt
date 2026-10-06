package com.darjnest.kinecare.feature.payment.presentation.util

import com.darjnest.kinecare.core.common.data.error.ConectarMercadoPagoError
import com.darjnest.kinecare.core.common.data.error.EstadoPagoError
import com.darjnest.kinecare.core.common.data.error.IniciarPagoError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MensajesPagoTest {

    @Test
    fun `cada IniciarPagoError tiene un mensaje propio en espanol`() {
        val mensajes = IniciarPagoError.entries.map(::mensajeErrorIniciar)
        assertTrue(mensajes.all { it.isNotBlank() })
        assertEquals(mensajes.size, mensajes.toSet().size, "dos errores distintos comparten mensaje")
    }

    @Test
    fun `PROFESIONAL_SIN_CUENTA_MP explica que el profesional aun no habilito los pagos`() {
        assertEquals(
            "El profesional aún no habilitó los pagos con Mercado Pago. Intenta más tarde.",
            mensajeErrorIniciar(IniciarPagoError.PROFESIONAL_SIN_CUENTA_MP),
        )
    }

    @Test
    fun `cada EstadoPagoError y ConectarMercadoPagoError tiene mensaje`() {
        assertTrue(EstadoPagoError.entries.all { mensajeErrorEstado(it).isNotBlank() })
        assertTrue(ConectarMercadoPagoError.entries.all { mensajeErrorConexion(it).isNotBlank() })
    }

    @Test
    fun `solo los errores transitorios de la consulta de estado se pueden reintentar`() {
        val reintentables = EstadoPagoError.entries.filter(::esReintentable).toSet()
        assertEquals(
            setOf(EstadoPagoError.SIN_INTERNET, EstadoPagoError.PASARELA_NO_DISPONIBLE, EstadoPagoError.DESCONOCIDO),
            reintentables,
        )
    }

    @Test
    fun `cada motivo conocido de la vuelta del OAuth tiene su mensaje`() {
        val motivos = listOf("estado_invalido", "cancelado", "sin_codigo", "pasarela")
        val mensajes = motivos.map(::mensajeMotivoVinculacion)
        assertEquals(motivos.size, mensajes.toSet().size)
        assertFalse(mensajes.contains(mensajeMotivoVinculacion(null)))
    }

    @Test
    fun `un motivo ausente o desconocido es un error generico, nunca exito`() {
        assertEquals(mensajeMotivoVinculacion(null), mensajeMotivoVinculacion("cualquier-cosa"))
        assertNotEquals("", mensajeMotivoVinculacion(null))
        assertTrue(mensajeMotivoVinculacion(null).startsWith("No pudimos vincular"))
    }

    @Test
    fun `solo se abren URLs https`() {
        assertTrue(esUrlHttps("https://www.mercadopago.cl/checkout/v1/redirect?pref_id=1"))
        assertFalse(esUrlHttps("http://www.mercadopago.cl"))
        assertFalse(esUrlHttps("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(esUrlHttps("kinecare://pago/resultado"))
        assertFalse(esUrlHttps(""))
    }
}
