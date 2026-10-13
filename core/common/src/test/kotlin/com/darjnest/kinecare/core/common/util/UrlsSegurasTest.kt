package com.darjnest.kinecare.core.common.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UrlsSegurasTest {

    private val dominios = listOf("ejemplo.cl", "otro.com")

    @Test
    fun `acepta https del dominio, de sus subdominios y de cualquiera de la lista`() {
        assertTrue(esUrlHttpsDeDominios("https://ejemplo.cl/ruta?x=1", dominios))
        assertTrue(esUrlHttpsDeDominios("https://www.ejemplo.cl", dominios))
        assertTrue(esUrlHttpsDeDominios("https://a.b.otro.com:443/x", dominios))
        assertTrue(esUrlHttpsDeDominios("HTTPS://EJEMPLO.CL", dominios))
    }

    @Test
    fun `rechaza otros esquemas`() {
        assertFalse(esUrlHttpsDeDominios("http://ejemplo.cl", dominios))
        assertFalse(esUrlHttpsDeDominios("javascript:alert(1)", dominios))
        assertFalse(esUrlHttpsDeDominios("intent://ejemplo.cl#Intent;end", dominios))
        assertFalse(esUrlHttpsDeDominios("", dominios))
    }

    @Test
    fun `rechaza hosts que solo se parecen al permitido`() {
        assertFalse(esUrlHttpsDeDominios("https://ejemplo.cl.evil.com", dominios))
        assertFalse(esUrlHttpsDeDominios("https://evil-ejemplo.cl", dominios))
        assertFalse(esUrlHttpsDeDominios("https://evil.com/?x=ejemplo.cl", dominios))
        assertFalse(esUrlHttpsDeDominios("https://ejemplo.cl@evil.com", dominios))
        assertFalse(esUrlHttpsDeDominios("https://ejemplo.cl:8443/x", dominios))
    }

    @Test
    fun `una URL mal formada o una lista vacia retornan false sin lanzar`() {
        assertFalse(esUrlHttpsDeDominios("https://[::1", dominios))
        assertFalse(esUrlHttpsDeDominios("https://ejemplo.cl/con espacio", dominios))
        assertFalse(esUrlHttpsDeDominios("https://ejemplo.cl", emptyList()))
    }
}
