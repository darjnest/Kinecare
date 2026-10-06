package com.darjnest.kinecare.feature.payment.presentation.util

/**
 * Las URLs de pago y de OAuth llegan del backend; igual se abren solo si son
 * `https`, para que una respuesta inesperada no lance otro esquema (`intent:`,
 * `file:`...) desde el Custom Tab.
 */
fun esUrlHttps(url: String): Boolean = url.startsWith("https://", ignoreCase = true)
