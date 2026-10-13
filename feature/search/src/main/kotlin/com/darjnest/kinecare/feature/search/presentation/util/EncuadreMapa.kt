package com.darjnest.kinecare.feature.search.presentation.util

import com.darjnest.kinecare.core.common.domain.model.Coordenada
import kotlin.math.abs

/** Distancia maxima, en grados (~55 km), a la mediana para que un punto cuente como parte del grupo principal. */
private const val RADIO_GRUPO_GRADOS = 0.5

/**
 * Puntos con los que se encuadra la camara del mapa: el grupo principal. Si un profesional esta a
 * cientos de kilometros del resto (p. ej. uno en Valdivia y seis en Santiago), encuadrar todos
 * alejaria el mapa hasta mostrar el pais entero y dejaria los pines del grupo amontonados. Los
 * lejanos siguen siendo pines del mapa; solo no deciden el zoom inicial.
 *
 * El centro del grupo es la mediana (no el promedio), que no se mueve por un caso atipico.
 */
internal fun puntosParaEncuadrar(puntos: List<Coordenada>): List<Coordenada> {
    if (puntos.size <= 2) return puntos
    val centro = Coordenada(mediana(puntos.map { it.latitud }), mediana(puntos.map { it.longitud }))
    val grupo = puntos.filter {
        abs(it.latitud - centro.latitud) <= RADIO_GRUPO_GRADOS && abs(it.longitud - centro.longitud) <= RADIO_GRUPO_GRADOS
    }
    return grupo.ifEmpty { puntos }
}

private fun mediana(valores: List<Double>): Double {
    val orden = valores.sorted()
    val medio = orden.size / 2
    return if (orden.size % 2 == 1) orden[medio] else (orden[medio - 1] + orden[medio]) / 2
}
