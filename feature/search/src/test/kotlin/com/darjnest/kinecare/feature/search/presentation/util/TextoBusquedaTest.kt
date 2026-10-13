package com.darjnest.kinecare.feature.search.presentation.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextoBusquedaTest {

    @Test
    fun `normaliza tildes, enie, mayusculas y espacios`() {
        assertEquals("nunoa y la reina", normalizarParaBusqueda("  ÑUÑOA   y La  Reína "))
        assertEquals("kinesiologia deportiva", normalizarParaBusqueda("Kinesiología Deportiva"))
    }

    @Test
    fun `una consulta vacia o en blanco coincide con todo`() {
        assertTrue(coincideConBusqueda("ana perez", ""))
        assertTrue(coincideConBusqueda("ana perez", "   "))
    }

    @Test
    fun `todas las palabras deben estar en el indice`() {
        assertTrue(coincideConBusqueda("ana perez kinesiologia", "PÉREZ ana"))
        assertFalse(coincideConBusqueda("ana perez kinesiologia", "ana diaz"))
    }
}
