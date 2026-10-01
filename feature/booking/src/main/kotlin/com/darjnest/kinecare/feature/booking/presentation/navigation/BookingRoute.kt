package com.darjnest.kinecare.feature.booking.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Flujo de reserva con un profesional. [servicioId] es opcional: si se
 * entra desde un servicio concreto del perfil, llega preseleccionado. Igual
 * que el resto de las rutas con argumentos, el `ViewModel` los lee del
 * `SavedStateHandle` con las claves `ARG_*` (sin `toRoute`).
 */
@Serializable
data class BookingRoute(val profesionalId: String, val servicioId: String? = null)

/** Debe coincidir con la propiedad `profesionalId` de [BookingRoute] (hay un test que lo verifica). */
const val ARG_PROFESIONAL_ID = "profesionalId"

/** Debe coincidir con la propiedad `servicioId` de [BookingRoute] (hay un test que lo verifica). */
const val ARG_SERVICIO_ID = "servicioId"
