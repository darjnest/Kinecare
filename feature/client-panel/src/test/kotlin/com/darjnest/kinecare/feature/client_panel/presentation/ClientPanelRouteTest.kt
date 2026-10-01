package com.darjnest.kinecare.feature.client_panel.presentation

import com.darjnest.kinecare.feature.client_panel.presentation.navigation.ARG_PROFESIONAL_ID
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.ARG_RESERVA_ID
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.ReportarProblemaRoute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `ReportarProblemaViewModel` lee los argumentos del `SavedStateHandle` con
 * las claves `ARG_*`; Navigation Compose tipado guarda cada propiedad de la
 * ruta bajo su nombre. Si alguien renombra una propiedad, esto falla.
 */
class ClientPanelRouteTest {

    @Test
    fun `ReportarProblemaRoute expone reservaId y profesionalId con las claves de los argumentos`() {
        val descriptor = ReportarProblemaRoute.serializer().descriptor
        assertEquals(ARG_RESERVA_ID, descriptor.getElementName(0))
        assertEquals(ARG_PROFESIONAL_ID, descriptor.getElementName(1))
    }
}
