package com.darjnest.kinecare.feature.payment.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Vuelta del OAuth por [MP_CONECTADO_DEEP_LINK]. Es una ruta distinta de
 * [MercadoPagoErrorRoute] a proposito: un deep link de error sin `motivo` no
 * debe poder interpretarse como exito. Ademas, la pantalla no da por conectada
 * la cuenta por el solo hecho de llegar aqui: lo comprueba en Firestore.
 */
@Serializable
data object MercadoPagoConectadoRoute

/** Destino de `mercadoPagoOAuthCallback` (Cloud Function) cuando el profesional autorizo la cuenta. */
const val MP_CONECTADO_DEEP_LINK = "kinecare://mp/conectado"
