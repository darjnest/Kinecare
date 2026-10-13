package com.darjnest.kinecare.feature.payment.presentation.util

import com.darjnest.kinecare.core.common.util.esUrlHttpsDeDominios

/**
 * Dominios (y sus subdominios) desde los que Mercado Pago sirve el Checkout
 * Pro y la autorizacion OAuth.
 */
private val DOMINIOS_MERCADO_PAGO = listOf(
    "mercadopago.cl",
    "mercadopago.com",
    "mercadolibre.cl",
    "mercadolibre.com",
)

/**
 * Las URLs de pago y de OAuth llegan del backend; igual se abren solo si son
 * `https` y su host es de Mercado Pago (la validacion de host, userinfo y
 * puerto es la compartida de `:core:common`, [esUrlHttpsDeDominios]), para que
 * una respuesta inesperada no lance otro esquema ni lleve al usuario a un sitio
 * de terceros desde el Custom Tab. Una URL mal formada retorna `false`.
 */
fun esUrlDeMercadoPago(url: String): Boolean = esUrlHttpsDeDominios(url, DOMINIOS_MERCADO_PAGO)
