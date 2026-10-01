package com.darjnest.kinecare.feature.client_panel.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Formulario para reportar un problema con una reserva desde "Mis Citas".
 * Igual que `CrearResenaRoute`, el `ViewModel` lee los argumentos del
 * `SavedStateHandle` con las claves [ARG_RESERVA_ID] y [ARG_PROFESIONAL_ID].
 */
@Serializable
data class ReportarProblemaRoute(val reservaId: String, val profesionalId: String)

/** Debe coincidir con la propiedad `reservaId` de [ReportarProblemaRoute] (hay un test que lo verifica). */
const val ARG_RESERVA_ID = "reservaId"

/** Debe coincidir con la propiedad `profesionalId` de [ReportarProblemaRoute] (hay un test que lo verifica). */
const val ARG_PROFESIONAL_ID = "profesionalId"
