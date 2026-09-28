package com.darjnest.kinecare.feature.reviews.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Listado de resenas de un profesional. Igual que
 * `ProfessionalProfileRoute`, el `ViewModel` lee el argumento del
 * `SavedStateHandle` con la clave [ARG_PROFESIONAL_ID] (Navigation Compose
 * tipado guarda cada propiedad de la ruta bajo su nombre), sin `toRoute`.
 */
@Serializable
data class ReviewsRoute(val profesionalId: String)

/**
 * Formulario para reseñar una atencion completada. El id de la resena en
 * Firestore es el [reservaId] (una resena por reserva).
 */
@Serializable
data class CrearResenaRoute(val reservaId: String, val profesionalId: String)

/** Debe coincidir con la propiedad `profesionalId` de [ReviewsRoute] y [CrearResenaRoute] (hay un test que lo verifica). */
const val ARG_PROFESIONAL_ID = "profesionalId"

/** Debe coincidir con la propiedad `reservaId` de [CrearResenaRoute] (hay un test que lo verifica). */
const val ARG_RESERVA_ID = "reservaId"
