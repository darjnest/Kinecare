package com.darjnest.kinecare.feature.reviews.presentation.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val localeChile: Locale = Locale.Builder().setLanguage("es").setRegion("CL").build()

/** "15 de marzo de 2026". */
fun formatearFecha(epochMillis: Long): String =
    SimpleDateFormat("d 'de' MMMM 'de' yyyy", localeChile).format(Date(epochMillis))

/** "4,8" (coma decimal, es-CL). */
fun formatearCalificacion(calificacion: Double): String =
    String.format(localeChile, "%.1f", calificacion)

fun textoResenas(total: Int): String = if (total == 1) "1 reseña" else "$total reseñas"

/** "3 de 5 estrellas", para lectores de pantalla. */
fun textoEstrellas(calificacion: Int): String =
    if (calificacion == 1) "1 estrella de 5" else "$calificacion estrellas de 5"

/** Nombre de respaldo cuando el autor no se puede resolver. */
const val AUTOR_ANONIMO = "Cliente"

/**
 * "Nombre A.": primer nombre + inicial del ultimo apellido/palabra del nombre
 * completo ("Ana Maria Soto" -> "Ana S."). Es lo unico del autor que se
 * muestra: nunca el nombre completo, ni email/RUT/telefono. Si el nombre esta
 * vacio o parece un email, devuelve [AUTOR_ANONIMO].
 */
fun nombreCorto(nombreCompleto: String?): String {
    val partes = nombreCompleto.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val primero = partes.firstOrNull() ?: return AUTOR_ANONIMO
    if ('@' in primero) return AUTOR_ANONIMO
    val nombre = primero.replaceFirstChar { it.uppercaseChar() }
    val apellido = partes.drop(1).lastOrNull()?.takeIf { '@' !in it }
    return if (apellido == null) nombre else "$nombre ${apellido.first().uppercaseChar()}."
}
