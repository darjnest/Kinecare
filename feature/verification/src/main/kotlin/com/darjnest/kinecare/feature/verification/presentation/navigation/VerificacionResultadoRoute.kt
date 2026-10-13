package com.darjnest.kinecare.feature.verification.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Resultado de la verificacion, al que el profesional vuelve desde el Custom Tab
 * por el deep link [VERIFICACION_RESULTADO_DEEP_LINK]`?solicitudId=<id>`.
 * [solicitudId] solo identifica la solicitud (y puede faltar): el estado nunca
 * sale del deep link, se consulta siempre al backend con `estadoVerificacion`.
 */
@Serializable
data class VerificacionResultadoRoute(val solicitudId: String? = null)

/** Debe coincidir con la propiedad `solicitudId` de [VerificacionResultadoRoute] (hay un test que lo verifica). */
const val ARG_SOLICITUD_ID = "solicitudId"

/** Destino de la funcion HTTPS a la que Didit redirige al terminar la verificacion en el navegador. */
const val VERIFICACION_RESULTADO_DEEP_LINK = "kinecare://verificacion/resultado"
