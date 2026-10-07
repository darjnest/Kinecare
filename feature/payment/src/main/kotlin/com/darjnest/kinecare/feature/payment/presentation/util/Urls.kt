package com.darjnest.kinecare.feature.payment.presentation.util

import java.net.URI

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
 * `https` y su host es de Mercado Pago, para que una respuesta inesperada no
 * lance otro esquema (`intent:`, `file:`...) ni lleve al usuario a un sitio de
 * terceros desde el Custom Tab.
 *
 * El host se obtiene parseando la URL (no con `contains`/`startsWith` sobre el
 * texto), asi que `https://mercadopago.cl.evil.com`, `https://evil.com/?x=mercadopago.cl`
 * y `https://mercadopago.cl@evil.com` (userinfo) no pasan. Una URL mal formada
 * (incluida la que usa `\` como separador, que los navegadores interpretan
 * distinto que `java.net.URI`) retorna `false` en vez de lanzar.
 */
fun esUrlDeMercadoPago(url: String): Boolean {
    val uri = try {
        URI(url)
    } catch (e: Exception) {
        return false
    }
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    if (uri.userInfo != null) return false
    if (uri.port != -1 && uri.port != 443) return false
    val host = uri.host?.lowercase() ?: return false
    return DOMINIOS_MERCADO_PAGO.any { dominio -> host == dominio || host.endsWith(".$dominio") }
}
