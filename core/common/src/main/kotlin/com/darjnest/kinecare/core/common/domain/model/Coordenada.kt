package com.darjnest.kinecare.core.common.domain.model

import kotlin.math.floor
import kotlin.math.round

/** Punto geografico en grados (WGS84). */
data class Coordenada(
    val latitud: Double,
    val longitud: Double,
) {
    /**
     * Centro de la celda de ~1 km (0,01 grados) que contiene este punto. Es lo unico que se
     * muestra de donde atiende un profesional: muchos atienden a domicilio y su ubicacion base
     * puede ser su casa. Determinista y sin ruido aleatorio: dos profesionales de la misma celda
     * caen en el mismo pin, sin que se pueda distinguir cual esta donde.
     */
    fun aproximada(): Coordenada = Coordenada(centroDeCelda(latitud), centroDeCelda(longitud))

    private companion object {
        const val CELDA_GRADOS = 0.01

        fun centroDeCelda(valor: Double): Double {
            val centro = floor(valor / CELDA_GRADOS) * CELDA_GRADOS + CELDA_GRADOS / 2
            return round(centro * 1000) / 1000 // quita el ruido de coma flotante (p. ej. -33.455000000000005)
        }
    }
}
