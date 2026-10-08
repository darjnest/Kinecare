package com.darjnest.kinecare.feature.payment.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Resultado del pago, al que el cliente vuelve desde el Custom Tab por el
 * deep link [PAGO_RESULTADO_DEEP_LINK]`?pagoId=<id>`. [pagoId] solo identifica
 * el pago: el estado nunca sale del deep link, se consulta siempre al backend.
 */
@Serializable
data class PagoResultadoRoute(val pagoId: String? = null)

/** Debe coincidir con la propiedad `pagoId` de [PagoResultadoRoute] (hay un test que lo verifica). */
const val ARG_PAGO_ID = "pagoId"

/** Destino de `retornoPago` (Cloud Function) tras pagar en Mercado Pago. */
const val PAGO_RESULTADO_DEEP_LINK = "kinecare://pago/resultado"
