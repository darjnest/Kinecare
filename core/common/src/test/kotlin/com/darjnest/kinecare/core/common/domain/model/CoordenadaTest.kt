package com.darjnest.kinecare.core.common.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class CoordenadaTest {

    @Test
    fun `la aproximada es el centro de la celda de 0,01 grados que contiene el punto`() {
        // Santiago oriente: -33.4569 cae en [-33.46, -33.45) y -70.598 en [-70.60, -70.59).
        assertEquals(Coordenada(-33.455, -70.595), Coordenada(-33.4569, -70.598).aproximada())
        // Hemisferio norte y valores positivos: la misma regla (floor) en el otro signo.
        assertEquals(Coordenada(10.015, 20.025), Coordenada(10.019, 20.021).aproximada())
    }

    @Test
    fun `nunca devuelve la ubicacion exacta ni se aleja mas de media celda de ella`() {
        val exactas = listOf(
            Coordenada(-33.4489, -70.6693),
            Coordenada(-39.8142, -73.2459),
            Coordenada(-33.4425, -70.5353),
            Coordenada(-33.455, -70.595), // justo el centro de una celda: igual no cambia de celda
        )
        exactas.forEach { exacta ->
            val aproximada = exacta.aproximada()
            assertTrue(abs(aproximada.latitud - exacta.latitud) <= 0.005 + 1e-9, "latitud de $exacta")
            assertTrue(abs(aproximada.longitud - exacta.longitud) <= 0.005 + 1e-9, "longitud de $exacta")
        }
        assertNotEquals(Coordenada(-33.4489, -70.6693), Coordenada(-33.4489, -70.6693).aproximada())
    }

    @Test
    fun `dos puntos de la misma celda dan el mismo pin y es determinista`() {
        val a = Coordenada(-33.4561, -70.5981)
        val b = Coordenada(-33.4598, -70.5902)
        assertEquals(a.aproximada(), b.aproximada())
        assertEquals(a.aproximada(), a.aproximada())
    }

    @Test
    fun `aproximar dos veces no cambia el resultado`() {
        val una = Coordenada(-33.4569, -70.598).aproximada()
        assertEquals(una, una.aproximada())
    }
}
