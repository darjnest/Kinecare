package com.darjnest.kinecare.feature.payment.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Pantalla "Pagar": el cliente paga una reserva `CONFIRMADA` desde Mis Citas.
 * [titulo] (nombre del servicio) y [montoClp] son solo informativos: el monto
 * real lo fija el backend en `iniciarPago` a partir de la reserva. Igual que el
 * resto de las rutas con argumentos, el `ViewModel` los lee del
 * `SavedStateHandle` con las claves `ARG_*` (sin `toRoute`).
 */
@Serializable
data class PaymentRoute(val reservaId: String, val titulo: String, val montoClp: Long)

/** Debe coincidir con la propiedad `reservaId` de [PaymentRoute] (hay un test que lo verifica). */
const val ARG_RESERVA_ID = "reservaId"

/** Debe coincidir con la propiedad `titulo` de [PaymentRoute] (hay un test que lo verifica). */
const val ARG_TITULO = "titulo"

/** Debe coincidir con la propiedad `montoClp` de [PaymentRoute] (hay un test que lo verifica). */
const val ARG_MONTO_CLP = "montoClp"
