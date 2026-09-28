package com.darjnest.kinecare.feature.reviews.presentation.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatosTest {

    @Test
    fun `nombreCorto usa el primer nombre y la inicial del ultimo apellido`() {
        assertEquals("Ana S.", nombreCorto("Ana Soto"))
        assertEquals("Ana R.", nombreCorto("Ana Maria Soto Rojas"))
        assertEquals("Ana S.", nombreCorto("  ana   soto  "))
    }

    @Test
    fun `nombreCorto con una sola palabra devuelve solo esa palabra`() {
        assertEquals("Ana", nombreCorto("Ana"))
    }

    @Test
    fun `nombreCorto cae a Cliente si el nombre esta vacio o es nulo`() {
        assertEquals(AUTOR_ANONIMO, nombreCorto(null))
        assertEquals(AUTOR_ANONIMO, nombreCorto("   "))
    }

    @Test
    fun `nombreCorto nunca expone un email`() {
        assertEquals(AUTOR_ANONIMO, nombreCorto("ana@kinecare.cl"))
        assertEquals("Ana", nombreCorto("Ana ana@kinecare.cl"))
    }

    @Test
    fun `textoResenas y textoEstrellas concuerdan en singular y plural`() {
        assertEquals("1 reseña", textoResenas(1))
        assertEquals("3 reseñas", textoResenas(3))
        assertEquals("1 estrella de 5", textoEstrellas(1))
        assertEquals("4 estrellas de 5", textoEstrellas(4))
    }

    @Test
    fun `formatearCalificacion usa coma decimal`() {
        assertEquals("4,5", formatearCalificacion(4.5))
    }
}
