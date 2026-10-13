package com.darjnest.kinecare.feature.search.presentation.util

import com.darjnest.kinecare.core.common.domain.model.Coordenada
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EncuadreMapaTest {

    private val santiago = listOf(
        Coordenada(-33.455, -70.595),
        Coordenada(-33.425, -70.605),
        Coordenada(-33.405, -70.565),
        Coordenada(-33.445, -70.535),
        Coordenada(-33.445, -70.665),
        Coordenada(-33.505, -70.755),
    )
    private val valdivia = Coordenada(-39.815, -73.245)

    @Test
    fun `un profesional lejano no entra en el encuadre del grupo principal`() {
        assertEquals(santiago, puntosParaEncuadrar(santiago + valdivia))
        assertEquals(santiago, puntosParaEncuadrar(listOf(valdivia) + santiago))
    }

    @Test
    fun `si todos estan cerca se encuadran todos`() {
        assertEquals(santiago, puntosParaEncuadrar(santiago))
    }

    @Test
    fun `con uno o dos puntos no se descarta ninguno, ni siquiera lejanos`() {
        assertEquals(emptyList<Coordenada>(), puntosParaEncuadrar(emptyList()))
        assertEquals(listOf(valdivia), puntosParaEncuadrar(listOf(valdivia)))
        assertEquals(listOf(santiago[0], valdivia), puntosParaEncuadrar(listOf(santiago[0], valdivia)))
    }

    @Test
    fun `dos grupos del mismo tamano se encuadran por la mediana sin perder el dato`() {
        val norte = listOf(Coordenada(-18.5, -70.3), Coordenada(-18.4, -70.2), Coordenada(-18.45, -70.25))
        val resultado = puntosParaEncuadrar(santiago.take(3) + norte)
        // Nunca queda vacio: se encuadra uno de los grupos, el otro sigue siendo pines del mapa.
        assertEquals(true, resultado.isNotEmpty())
    }
}
