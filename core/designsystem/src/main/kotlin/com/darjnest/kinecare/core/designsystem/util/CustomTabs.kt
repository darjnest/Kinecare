package com.darjnest.kinecare.core.designsystem.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Abre [url] en un Custom Tab (el pago, el OAuth y la verificacion de identidad
 * nunca van en un WebView). Devuelve `false` si el dispositivo no tiene ningun
 * navegador. No valida la URL: quien la recibe del backend la valida antes con
 * `esUrlHttpsDeDominios` (`:core:common`) y los dominios de su proveedor. La URL
 * no se registra en ningun log: las de pago, OAuth y verificacion son sensibles.
 */
fun abrirEnCustomTab(context: Context, url: String): Boolean = try {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    true
} catch (e: ActivityNotFoundException) {
    false
}
