package com.darjnest.kinecare.core.common.util

import java.net.URI

/**
 * Las URLs que el backend entrega para abrir en un Custom Tab (pago, OAuth,
 * verificacion de identidad) se abren solo si son `https` y su host es uno de
 * [dominiosPermitidos] (o un subdominio), para que una respuesta inesperada no
 * lance otro esquema (`intent:`, `file:`, `javascript:`...) ni lleve al usuario
 * a un sitio de terceros.
 *
 * El host se obtiene parseando la URL (no con `contains`/`startsWith` sobre el
 * texto), asi que `https://didit.me.evil.com`, `https://evil.com/?x=didit.me`,
 * `https://didit.me@evil.com` (userinfo) y `https://evil-didit.me` no pasan para
 * `didit.me`. Una URL mal formada (incluida la que usa `\` como separador, que
 * los navegadores interpretan distinto que `java.net.URI`) retorna `false` en
 * vez de lanzar.
 *
 * Los [dominiosPermitidos] se escriben en minuscula y sin esquema ni punto
 * inicial (`"didit.me"`).
 */
fun esUrlHttpsDeDominios(url: String, dominiosPermitidos: List<String>): Boolean {
    val uri = try {
        URI(url)
    } catch (e: Exception) {
        return false
    }
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    if (uri.userInfo != null) return false
    if (uri.port != -1 && uri.port != 443) return false
    val host = uri.host?.lowercase() ?: return false
    return dominiosPermitidos.any { dominio -> host == dominio || host.endsWith(".$dominio") }
}
