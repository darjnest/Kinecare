package com.darjnest.kinecare.feature.reviews.presentation

import com.darjnest.kinecare.feature.reviews.presentation.navigation.ARG_PROFESIONAL_ID
import com.darjnest.kinecare.feature.reviews.presentation.navigation.ARG_RESERVA_ID
import com.darjnest.kinecare.feature.reviews.presentation.navigation.CrearResenaRoute
import com.darjnest.kinecare.feature.reviews.presentation.navigation.ReviewsRoute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Los `ViewModel` leen los argumentos del `SavedStateHandle` con las claves
 * `ARG_*`; Navigation Compose tipado guarda cada propiedad de la ruta bajo
 * su nombre. Si alguien renombra una propiedad, esto falla en vez de dejar
 * la pantalla sin id en runtime.
 */
class ReviewsRouteTest {

    @Test
    fun `ReviewsRoute expone profesionalId con la clave del argumento`() {
        assertEquals(ARG_PROFESIONAL_ID, ReviewsRoute.serializer().descriptor.getElementName(0))
    }

    @Test
    fun `CrearResenaRoute expone reservaId y profesionalId con las claves de los argumentos`() {
        val descriptor = CrearResenaRoute.serializer().descriptor
        assertEquals(ARG_RESERVA_ID, descriptor.getElementName(0))
        assertEquals(ARG_PROFESIONAL_ID, descriptor.getElementName(1))
    }
}
