package com.darjnest.kinecare.feature.payment.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Vuelta del OAuth con error por [MP_ERROR_DEEP_LINK]`?motivo=<motivo>`
 * (`estado_invalido`, `cancelado`, `sin_codigo`, `pasarela`). [motivo] es
 * opcional y puede ser cualquier texto: solo elige el mensaje, nunca implica exito.
 */
@Serializable
data class MercadoPagoErrorRoute(val motivo: String? = null)

/** Debe coincidir con la propiedad `motivo` de [MercadoPagoErrorRoute] (hay un test que lo verifica). */
const val ARG_MOTIVO = "motivo"

/** Destino de `mercadoPagoOAuthCallback` (Cloud Function) cuando la vinculacion falla o se cancela. */
const val MP_ERROR_DEEP_LINK = "kinecare://mp/error"
