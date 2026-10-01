package com.darjnest.kinecare.feature.booking.presentation.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * El `ViewModel` lee los argumentos del `SavedStateHandle` con las claves
 * `ARG_*`; Navigation Compose tipado guarda cada propiedad de la ruta bajo
 * su nombre. Si alguien renombra una propiedad, esto falla en vez de dejar
 * la pantalla sin id en runtime.
 */
class BookingRouteTest {

    @Test
    fun `BookingRoute expone profesionalId y servicioId con las claves de los argumentos`() {
        val descriptor = BookingRoute.serializer().descriptor
        assertEquals(ARG_PROFESIONAL_ID, descriptor.getElementName(0))
        assertEquals(ARG_SERVICIO_ID, descriptor.getElementName(1))
    }

    @Test
    fun `servicioId es opcional en la ruta`() {
        assertEquals(null, BookingRoute(profesionalId = "prof-1").servicioId)
    }
}
