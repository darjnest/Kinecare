package com.darjnest.kinecare.feature.payment.domain

/**
 * Cobro de comisiones adeudadas creado por `pagarSaldoComision`: [urlPago] es el
 * Checkout Pro de Mercado Pago (a la cuenta de KineCare) y [monto] lo que se cobra
 * en CLP. Lo fija el backend; el profesional nunca lo envia.
 */
data class CobroComision(
    val cobroId: String,
    val urlPago: String,
    val monto: Long,
)
