package com.darjnest.kinecare.feature.verification.presentation.util

import com.darjnest.kinecare.core.common.util.esUrlHttpsDeDominios

/** Dominios (y sus subdominios) desde los que Didit hospeda la verificacion (`verify.didit.me`). */
private val DOMINIOS_DIDIT = listOf("didit.me")

/**
 * La URL de verificacion llega del backend; igual se abre solo si es `https` y
 * su host es de Didit (la validacion de host, userinfo y puerto es la
 * compartida de `:core:common`, [esUrlHttpsDeDominios]), para que una
 * respuesta inesperada no lance otro esquema (`intent:`, `javascript:`...) ni
 * lleve al profesional a un sitio de terceros desde el Custom Tab. Una URL mal
 * formada retorna `false`.
 */
fun esUrlDeDidit(url: String): Boolean = esUrlHttpsDeDominios(url, DOMINIOS_DIDIT)
