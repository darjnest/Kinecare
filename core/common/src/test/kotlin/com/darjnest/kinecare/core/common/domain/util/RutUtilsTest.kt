package com.darjnest.kinecare.core.common.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RutUtilsTest {

    @Test
    fun `normaliza quitando puntos, guion y pasando a mayuscula`() {
        assertEquals("123456785", RutUtils.normalizar("12.345.678-5"))
        assertEquals("6K", RutUtils.normalizar("6-k"))
    }

    @Test
    fun `rut con digito verificador numerico valido`() {
        assertTrue(RutUtils.esValido("12.345.678-5"))
    }

    @Test
    fun `rut con digito verificador K valido`() {
        assertTrue(RutUtils.esValido("6-K"))
        assertTrue(RutUtils.esValido("6-k"))
    }

    @Test
    fun `rut con digito verificador incorrecto es invalido`() {
        assertFalse(RutUtils.esValido("12.345.678-9"))
    }

    @Test
    fun `rut con cuerpo no numerico es invalido`() {
        assertFalse(RutUtils.esValido("12A45678-5"))
    }

    @Test
    fun `rut demasiado corto es invalido`() {
        assertFalse(RutUtils.esValido("1"))
        assertFalse(RutUtils.esValido(""))
    }

    @Test
    fun `deriva un email interno estable a partir del rut`() {
        assertEquals("123456785@rut.kinecare.cl", RutUtils.emailFirebase("12.345.678-5"))
        assertEquals("6k@rut.kinecare.cl", RutUtils.emailFirebase("6-K"))
    }
}
