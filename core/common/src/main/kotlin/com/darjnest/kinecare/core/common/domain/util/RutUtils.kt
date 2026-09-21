package com.darjnest.kinecare.core.common.domain.util

/**
 * Validación y utilidades del RUT chileno. El login de KineCare usa el RUT
 * como único identificador de cuenta; Firebase Auth exige un email, así que
 * [emailFirebase] deriva un correo interno sintético a partir del RUT — ese
 * correo nunca se muestra al usuario ni se usa fuera de la llamada al SDK.
 */
object RutUtils {

    fun normalizar(rut: String): String =
        rut.trim().replace(".", "").replace("-", "").uppercase()

    fun esValido(rut: String): Boolean {
        val limpio = normalizar(rut)
        if (limpio.length < 2) return false
        val cuerpo = limpio.dropLast(1)
        val dv = limpio.last()
        if (cuerpo.isEmpty() || !cuerpo.all { it.isDigit() }) return false
        return calcularDv(cuerpo) == dv
    }

    fun emailFirebase(rut: String): String = "${normalizar(rut).lowercase()}@rut.kinecare.cl"

    private fun calcularDv(cuerpo: String): Char {
        var suma = 0
        var multiplicador = 2
        for (c in cuerpo.reversed()) {
            suma += (c - '0') * multiplicador
            multiplicador = if (multiplicador == 7) 2 else multiplicador + 1
        }
        return when (val resto = 11 - (suma % 11)) {
            11 -> '0'
            10 -> 'K'
            else -> '0' + resto
        }
    }
}
