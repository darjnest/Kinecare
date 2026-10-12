package com.darjnest.kinecare.feature.search.presentation.util

import java.text.Normalizer

private val MARCAS_DIACRITICAS = Regex("\\p{M}+")
private val ESPACIOS = Regex("\\s+")

/**
 * Minusculas, sin tildes ni dieresis (la `n` con tilde tambien pierde la
 * marca) y con los espacios colapsados: la forma en que se compara el texto
 * escrito por el cliente con el de cada profesional.
 */
internal fun normalizarParaBusqueda(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(MARCAS_DIACRITICAS, "")
        .lowercase()
        .replace(ESPACIOS, " ")
        .trim()

/**
 * `true` si cada palabra de [consulta] aparece en [indice] (ya normalizado con
 * [normalizarParaBusqueda]). Una consulta vacia coincide con todo.
 */
internal fun coincideConBusqueda(indice: String, consulta: String): Boolean {
    val palabras = normalizarParaBusqueda(consulta).split(' ').filter { it.isNotEmpty() }
    return palabras.all { it in indice }
}
