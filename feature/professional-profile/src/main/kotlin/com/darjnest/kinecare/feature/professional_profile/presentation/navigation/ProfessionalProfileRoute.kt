package com.darjnest.kinecare.feature.professional_profile.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Destino del perfil publico de un profesional. Es el primer destino del
 * proyecto con argumento: el `ViewModel` lo lee del `SavedStateHandle` con
 * la clave [ARG_PROFESIONAL_ID] (Navigation Compose tipado guarda cada
 * propiedad de la ruta bajo su nombre), sin depender de `toRoute`, que
 * necesita un `Bundle` real y no corre en tests JVM puros.
 */
@Serializable
data class ProfessionalProfileRoute(val profesionalId: String)

/** Debe coincidir con el nombre de la propiedad de [ProfessionalProfileRoute] (hay un test que lo verifica). */
const val ARG_PROFESIONAL_ID = "profesionalId"
