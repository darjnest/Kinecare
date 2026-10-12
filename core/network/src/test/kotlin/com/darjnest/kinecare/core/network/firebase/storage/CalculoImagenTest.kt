package com.darjnest.kinecare.core.network.firebase.storage

import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalculoImagenTest {

    @Test
    fun `la ruta del profesional coincide con storage rules`() {
        assertEquals("profesionales/u1/foto-perfil.jpg", rutaFotoPerfil("u1", RolUsuario.PROFESIONAL))
    }

    @Test
    fun `la ruta del cliente va bajo clientes`() {
        assertEquals("clientes/u1/foto-perfil.jpg", rutaFotoPerfil("u1", RolUsuario.CLIENTE))
    }

    @Test
    fun `una imagen que ya cabe no se agranda ni se reduce`() {
        assertEquals(800 to 600, dimensionesReducidas(800, 600))
        assertEquals(1280 to 720, dimensionesReducidas(1280, 720))
    }

    @Test
    fun `una foto apaisada baja su lado mayor a 1280 conservando la proporcion`() {
        assertEquals(1280 to 960, dimensionesReducidas(4000, 3000))
    }

    @Test
    fun `una foto vertical baja su lado mayor a 1280 conservando la proporcion`() {
        assertEquals(960 to 1280, dimensionesReducidas(3000, 4000))
    }

    @Test
    fun `una imagen extremadamente alargada nunca queda con un lado en cero`() {
        val (ancho, alto) = dimensionesReducidas(20000, 5)
        assertEquals(1280, ancho)
        assertTrue(alto >= 1)
    }

    @Test
    fun `una imagen pequena se decodifica completa`() {
        assertEquals(1, calcularInSampleSize(1000, 800))
        assertEquals(1, calcularInSampleSize(2559, 2000))
    }

    @Test
    fun `el inSampleSize es potencia de dos y deja el lado mayor en 1280 o mas`() {
        assertEquals(2, calcularInSampleSize(4000, 3000))
        assertEquals(4, calcularInSampleSize(5120, 3840))
        assertEquals(8, calcularInSampleSize(12000, 9000))
    }

    @Test
    fun `la version se agrega como parametro de consulta`() {
        assertEquals("https://x/y?alt=media&token=t&v=7", conVersion("https://x/y?alt=media&token=t", 7))
        assertEquals("https://x/y?v=7", conVersion("https://x/y", 7))
    }
}
