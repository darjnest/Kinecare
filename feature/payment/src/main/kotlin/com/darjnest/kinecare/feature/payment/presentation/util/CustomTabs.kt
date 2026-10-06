package com.darjnest.kinecare.feature.payment.presentation.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Abre [url] en un Custom Tab (el pago y el OAuth de Mercado Pago nunca van en
 * un WebView). Devuelve `false` si el dispositivo no tiene ningun navegador.
 * La URL no se registra en ningun log: la de pago y la de OAuth son sensibles.
 */
fun abrirEnCustomTab(context: Context, url: String): Boolean = try {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    true
} catch (e: ActivityNotFoundException) {
    false
}
