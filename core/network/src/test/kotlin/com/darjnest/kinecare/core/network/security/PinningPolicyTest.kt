package com.darjnest.kinecare.core.network.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Call
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.io.encoding.Base64

class PinningPolicyTest {

    private val dia = 24L * 60 * 60
    private val par = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }
    private val clave = Base64.encode(par.public.encoded)
    private val inicio = 1_800_000_000L

    private fun override(desde: Long, hasta: Long): String {
        val firma = Signature.getInstance("SHA256withECDSA").run {
            initSign(par.private)
            update(mensajeOverrideFirmado(desde, hasta).toByteArray(Charsets.UTF_8))
            Base64.encode(sign())
        }
        return """{"v":1,"desde":$desde,"hasta":$hasta,"firma":"$firma"}"""
    }

    private var valor: String? = null
    private var ahora = inicio
    private val politica = PinningPolicy(valorOverride = { valor }, ahoraEpochSegundos = { ahora }, clavePublica = clave)

    @Test
    fun `sin override el pinning esta activo`() {
        assertTrue(politica.pinningActivo())
    }

    @Test
    fun `un override valido lo desactiva y vuelve a activarse solo al vencer`() {
        valor = override(inicio, inicio + 2 * dia)

        assertFalse(politica.pinningActivo())
        ahora = inicio + dia
        assertFalse(politica.pinningActivo())
        ahora = inicio + 2 * dia
        assertTrue(politica.pinningActivo(), "vencido: el pinning vuelve solo, sin tocar Remote Config")
    }

    @Test
    fun `retirar el override en Remote Config reactiva el pinning de inmediato, sin esperar al vencimiento`() {
        valor = override(inicio, inicio + 20 * dia)
        assertFalse(politica.pinningActivo())

        valor = null

        assertTrue(politica.pinningActivo(), "el cache no debe sobrevivir al valor que lo origino")
    }

    @Test
    fun `reemplazar el override por uno invalido reactiva el pinning`() {
        valor = override(inicio, inicio + 20 * dia)
        assertFalse(politica.pinningActivo())

        valor = override(inicio, inicio + 20 * dia).replace("\"hasta\":${inicio + 20 * dia}", "\"hasta\":${inicio + 25 * dia}")

        assertTrue(politica.pinningActivo())
    }

    @Test
    fun `un override programado para mas adelante se activa solo cuando llega su desde`() {
        valor = override(inicio + 3 * dia, inicio + 5 * dia)

        assertTrue(politica.pinningActivo(), "aun no empieza")
        ahora = inicio + 4 * dia
        assertFalse(politica.pinningActivo())
    }

    @Test
    fun `basura en Remote Config no desactiva el pinning`() {
        listOf("", "false", "true", "0", "{\"v\":1}", "desactivar").forEach {
            valor = it
            assertTrue(politica.pinningActivo(), "no deberia desactivar con: $it")
        }
    }

    @Test
    fun `con la clave de fabrica (vacia) ningun override desactiva el pinning`() {
        val deFabrica = PinningPolicy(valorOverride = { override(inicio, inicio + dia) }, ahoraEpochSegundos = { inicio + 1 }, clavePublica = "")

        assertTrue(deFabrica.pinningActivo())
    }

    // --- PinningCallFactory --------------------------------------------------------------------

    private val solicitud = Request.Builder().url("https://us-central1-kinecare-cl-qa.cloudfunctions.net/").build()

    @Test
    fun `la fabrica usa el cliente con pinning mientras no haya override y el otro mientras lo haya`() {
        val llamadaConPinning = mockk<Call>()
        val llamadaSinPinning = mockk<Call>()
        val conPinning = mockk<Call.Factory> { every { newCall(any()) } returns llamadaConPinning }
        val sinPinning = mockk<Call.Factory> { every { newCall(any()) } returns llamadaSinPinning }
        val fabrica = PinningCallFactory(conPinning, sinPinning, politica)

        assertSame(llamadaConPinning, fabrica.newCall(solicitud))

        valor = override(inicio, inicio + dia)
        assertSame(llamadaSinPinning, fabrica.newCall(solicitud))

        valor = null
        assertSame(llamadaConPinning, fabrica.newCall(solicitud), "al retirarlo vuelve al cliente con pinning")
        verify(exactly = 2) { conPinning.newCall(any()) }
        verify(exactly = 1) { sinPinning.newCall(any()) }
    }
}
