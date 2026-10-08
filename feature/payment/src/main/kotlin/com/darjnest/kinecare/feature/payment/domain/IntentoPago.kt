package com.darjnest.kinecare.feature.payment.domain

/**
 * Intento de cobro creado por la Cloud Function `iniciarPago`: [pagoId] es el
 * documento `pagos/{pagoId}` y [urlPago] la pagina de Checkout Pro de Mercado
 * Pago (`init_point`) donde el cliente paga. El monto y la comision los fija el
 * backend; el cliente nunca los envia.
 */
data class IntentoPago(
    val pagoId: String,
    val urlPago: String,
)
