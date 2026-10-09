package com.darjnest.kinecare.core.network.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.io.encoding.Base64

class PinningOverrideTest {

    private val dia = 24L * 60 * 60

    private fun nuevoPar(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private fun clavePublica(par: KeyPair): String = Base64.encode(par.public.encoded)

    /** Firma igual que `scripts/pinning-override.sh` (ECDSA P-256 + SHA-256, firma DER en base64). */
    private fun override(par: KeyPair, desde: Long, hasta: Long, version: Int = 1, mensaje: String = mensajeOverrideFirmado(desde, hasta)): String {
        val firma = Signature.getInstance("SHA256withECDSA").run {
            initSign(par.private)
            update(mensaje.toByteArray(Charsets.UTF_8))
            Base64.encode(sign())
        }
        return """{"v":$version,"desde":$desde,"hasta":$hasta,"firma":"$firma"}"""
    }

    private fun fixture(nombre: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("pinning-override/$nombre")) { "falta $nombre" }
            .use { it.readBytes().toString(Charsets.UTF_8).trim() }

    private val par = nuevoPar()
    private val clave = clavePublica(par)
    private val desde = 1_800_000_000L

    // --- Compatibilidad real con el script ------------------------------------------------------

    @Test
    fun `un override firmado por scripts-pinning-override con OpenSSL lo acepta la verificacion de la app`() {
        val valor = fixture("override-firmado-openssl-7-dias.json")
        val claveFixture = fixture("clave-publica-prueba.b64")
        val inicio = Regex("\"desde\":(\\d+)").find(valor)!!.groupValues[1].toLong()
        val fin = Regex("\"hasta\":(\\d+)").find(valor)!!.groupValues[1].toLong()

        assertEquals(7 * dia, fin - inicio)
        assertTrue(overrideVigente(valor, inicio + dia, claveFixture))
        assertFalse(overrideVigente(valor, fin, claveFixture), "vencido en `hasta`")
        assertFalse(overrideVigente(valor, inicio + dia, clave), "firmado con otra clave")
    }

    // --- Ventana de vigencia --------------------------------------------------------------------

    @Test
    fun `vigente dentro de la ventana, no antes de desde ni desde hasta`() {
        val valor = override(par, desde, desde + 7 * dia)

        assertTrue(overrideVigente(valor, desde + 1, clave))
        assertTrue(overrideVigente(valor, desde + 7 * dia - 1, clave))
        assertFalse(overrideVigente(valor, desde + 7 * dia, clave), "`hasta` es exclusivo")
        assertFalse(overrideVigente(valor, desde + 8 * dia, clave))
        assertFalse(overrideVigente(valor, desde - dia, clave), "aun no empieza")
    }

    @Test
    fun `un reloj atrasado unos minutos no deja esperando a un override ya publicado`() {
        val valor = override(par, desde, desde + dia)

        assertTrue(overrideVigente(valor, desde - 60, clave))
        assertFalse(overrideVigente(valor, desde - TOLERANCIA_RELOJ_SEGUNDOS - 1, clave))
    }

    @Test
    fun `una ventana de mas de 30 dias se rechaza aunque la firma sea valida`() {
        assertTrue(overrideVigente(override(par, desde, desde + 30 * dia), desde + dia, clave))
        assertFalse(overrideVigente(override(par, desde, desde + 30 * dia + 1), desde + dia, clave))
        assertFalse(overrideVigente(override(par, desde, desde + 365 * dia), desde + dia, clave))
    }

    @Test
    fun `una ventana vacia o invertida se rechaza`() {
        assertFalse(overrideVigente(override(par, desde, desde), desde, clave))
        assertFalse(overrideVigente(override(par, desde + dia, desde), desde + 1, clave))
    }

    // --- Autenticidad ---------------------------------------------------------------------------

    @Test
    fun `una firma de otra clave se rechaza`() {
        val valor = override(nuevoPar(), desde, desde + dia)

        assertFalse(overrideVigente(valor, desde + 1, clave))
    }

    @Test
    fun `alterar desde o hasta invalida la firma, asi no se puede extender un override`() {
        val valor = override(par, desde, desde + dia)
        val extendido = valor.replace("\"hasta\":${desde + dia}", "\"hasta\":${desde + 10 * dia}")
        val adelantado = valor.replace("\"desde\":$desde", "\"desde\":${desde - dia}")

        assertTrue(overrideVigente(valor, desde + 1, clave))
        assertFalse(overrideVigente(extendido, desde + 2 * dia, clave))
        assertFalse(overrideVigente(adelantado, desde - dia + 1, clave))
    }

    @Test
    fun `una version distinta de la esperada se rechaza aunque este firmada`() {
        assertFalse(overrideVigente(override(par, desde, desde + dia, version = 2), desde + 1, clave))
    }

    @Test
    fun `lo firmado para otro mensaje no vale`() {
        val valor = override(par, desde, desde + dia, mensaje = "kinecare-pinning-override|v1|$desde|${desde + 99 * dia}")

        assertFalse(overrideVigente(valor, desde + 1, clave))
    }

    @Test
    fun `sin clave publica configurada nada se acepta, que es el estado de fabrica`() {
        val valor = override(par, desde, desde + dia)

        assertFalse(overrideVigente(valor, desde + 1, ""))
        assertFalse(overrideVigente(valor, desde + 1, "   "))
        assertTrue(CLAVE_PUBLICA_OVERRIDE_PINNING.isBlank() || CLAVE_PUBLICA_OVERRIDE_PINNING.length > 60)
    }

    // --- Basura: ante la duda, el pinning sigue activo ------------------------------------------

    @Test
    fun `valores vacios o corruptos nunca lanzan y no cuentan como override`() {
        val firma = Base64.encode(ByteArray(70) { 1 })
        listOf(
            null,
            "",
            "   ",
            "no es json",
            "{}",
            """{"v":1}""",
            """{"v":1,"desde":1,"hasta":2}""",
            """{"v":"uno","desde":1,"hasta":2,"firma":"x"}""",
            """{"v":1,"desde":$desde,"hasta":${desde + dia},"firma":"%%%no-base64%%%"}""",
            """{"v":1,"desde":$desde,"hasta":${desde + dia},"firma":"$firma"}""",
            """{"v":1,"desde":$desde,"hasta":${desde + dia},"firma":""}""",
            "true",
            "[]",
        ).forEach { valor ->
            assertFalse(overrideVigente(valor, desde + 1, clave), "no deberia aceptar: $valor")
        }
    }

    @Test
    fun `una clave publica invalida no hace lanzar y no acepta nada`() {
        val valor = override(par, desde, desde + dia)

        assertFalse(overrideVigente(valor, desde + 1, "esto-no-es-una-clave"))
        assertFalse(overrideVigente(valor, desde + 1, Base64.encode(ByteArray(40) { 7 })))
    }

    @Test
    fun `campos desconocidos se ignoran, para poder ampliar el formato sin romper versiones viejas`() {
        val valor = override(par, desde, desde + dia).replace("{\"v\":1,", "{\"v\":1,\"nota\":\"rotacion de CA\",")

        assertTrue(overrideVigente(valor, desde + 1, clave))
    }
}
