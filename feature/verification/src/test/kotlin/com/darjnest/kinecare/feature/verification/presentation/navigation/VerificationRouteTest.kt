package com.darjnest.kinecare.feature.verification.presentation.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * `VerificacionResultadoViewModel` lee el argumento del `SavedStateHandle` con
 * la clave `ARG_SOLICITUD_ID`; Navigation Compose tipado guarda cada propiedad
 * de la ruta bajo su nombre. Si alguien renombra la propiedad, esto falla en
 * vez de dejar la pantalla sin dato en runtime.
 */
class VerificationRouteTest {

    @Test
    fun `VerificacionResultadoRoute expone solicitudId con la clave del argumento y es opcional`() {
        assertEquals(ARG_SOLICITUD_ID, VerificacionResultadoRoute.serializer().descriptor.getElementName(0))
        assertNull(VerificacionResultadoRoute().solicitudId)
    }

    @Test
    fun `el deep link usa el esquema kinecare del contrato del backend`() {
        assertEquals("kinecare://verificacion/resultado", VERIFICACION_RESULTADO_DEEP_LINK)
    }
}
