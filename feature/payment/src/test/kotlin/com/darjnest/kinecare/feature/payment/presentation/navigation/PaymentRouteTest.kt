package com.darjnest.kinecare.feature.payment.presentation.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Los `ViewModel` leen los argumentos del `SavedStateHandle` con las claves
 * `ARG_*`; Navigation Compose tipado guarda cada propiedad de la ruta bajo su
 * nombre. Si alguien renombra una propiedad, esto falla en vez de dejar la
 * pantalla sin datos en runtime.
 */
class PaymentRouteTest {

    @Test
    fun `PaymentRoute expone reservaId, titulo y montoClp con las claves de los argumentos`() {
        val descriptor = PaymentRoute.serializer().descriptor
        assertEquals(ARG_RESERVA_ID, descriptor.getElementName(0))
        assertEquals(ARG_TITULO, descriptor.getElementName(1))
        assertEquals(ARG_MONTO_CLP, descriptor.getElementName(2))
    }

    @Test
    fun `PagoResultadoRoute expone pagoId con la clave del argumento y es opcional`() {
        assertEquals(ARG_PAGO_ID, PagoResultadoRoute.serializer().descriptor.getElementName(0))
        assertNull(PagoResultadoRoute().pagoId)
    }

    @Test
    fun `MercadoPagoErrorRoute expone motivo con la clave del argumento y es opcional`() {
        assertEquals(ARG_MOTIVO, MercadoPagoErrorRoute.serializer().descriptor.getElementName(0))
        assertNull(MercadoPagoErrorRoute().motivo)
    }

    @Test
    fun `los deep links usan el esquema kinecare del contrato del backend`() {
        assertEquals("kinecare://pago/resultado", PAGO_RESULTADO_DEEP_LINK)
        assertEquals("kinecare://mp/conectado", MP_CONECTADO_DEEP_LINK)
        assertEquals("kinecare://mp/error", MP_ERROR_DEEP_LINK)
    }
}
