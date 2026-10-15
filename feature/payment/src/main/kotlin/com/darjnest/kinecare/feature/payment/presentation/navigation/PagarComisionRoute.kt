package com.darjnest.kinecare.feature.payment.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Pantalla donde el profesional paga las comisiones que adeuda por atenciones en efectivo
 * (Checkout Pro de la cuenta de KineCare, en Custom Tab). El monto lo calcula el backend.
 */
@Serializable
data object PagarComisionRoute

/**
 * Vuelta de Mercado Pago tras pagar comisiones por [COMISION_RESULTADO_DEEP_LINK]. Sin parametros:
 * la pantalla no da el pago por hecho (lo confirma el webhook) y el saldo real se ve en Finanzas.
 */
@Serializable
data object ComisionResultadoRoute

/** Destino de `retornoPago?tipo=comision` (Cloud Function). */
const val COMISION_RESULTADO_DEEP_LINK = "kinecare://comision/resultado"
