package com.darjnest.kinecare.core.network.security

import com.darjnest.kinecare.core.network.di.NetworkModule
import com.darjnest.kinecare.core.network.urlBaseCloudFunctions
import okhttp3.CertificatePinner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.URI
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Los certificados de `src/test/resources/pinning` son publicos (raices de Google Trust Services
 * tomadas del almacen de confianza del sistema, y el intermedio `WR2` y la raiz R1 con firma cruzada
 * de GlobalSign tal como los sirvio `us-central1-kinecare-cl-qa.cloudfunctions.net` el 2026-10-08).
 * Son datos reales a proposito: un test con claves inventadas no detectaria un pin mal copiado.
 */
class CertificatePinsTest {

    private val pinner = crearCertificatePinnerCloudFunctions()

    private val hostQa = URI(urlBaseCloudFunctions("kinecare-cl-qa")).host
    private val hostProd = URI(urlBaseCloudFunctions("kinecare-cl")).host

    private fun cargar(archivo: String): Certificate {
        val flujo = checkNotNull(javaClass.classLoader?.getResourceAsStream("pinning/$archivo")) {
            "falta el fixture pinning/$archivo"
        }
        return flujo.use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
    }

    private val raices = mapOf(
        "R1" to "gts-root-r1.pem",
        "R2" to "gts-root-r2.pem",
        "R3" to "gts-root-r3.pem",
        "R4" to "gts-root-r4.pem",
    )

    @Test
    fun `los pins son exactamente las claves de las cuatro raices de Google Trust Services`() {
        val calculados = raices.values.map { CertificatePinner.pin(cargar(it)) }

        assertEquals(calculados.toSet(), PINS_CLOUD_FUNCTIONS.toSet())
        assertEquals(4, PINS_CLOUD_FUNCTIONS.size)
    }

    @Test
    fun `la raiz R1 con firma cruzada de GlobalSign que sirve Google tiene la clave pineada`() {
        assertEquals(CertificatePinner.pin(cargar("gts-root-r1.pem")), CertificatePinner.pin(cargar("gts-root-r1-cruzada-globalsign.pem")))
    }

    @Test
    fun `la cadena que sirve hoy el host de QA y de produccion es aceptada`() {
        val cadena = listOf(cargar("gts-wr2-intermedio.pem"), cargar("gts-root-r1-cruzada-globalsign.pem"))

        pinner.check(hostQa, cadena)
        pinner.check(hostProd, cadena)
    }

    @Test
    fun `cada raiz de Google basta por si sola, asi un cambio de linea de certificacion no bloquea la app`() {
        raices.forEach { (nombre, archivo) ->
            pinner.check(hostQa, listOf(cargar(archivo)))
            pinner.check(hostProd, listOf(cargar(archivo)))
            assertEquals(true, CertificatePinner.pin(cargar(archivo)) in PINS_CLOUD_FUNCTIONS, "raiz $nombre sin pin")
        }
    }

    @Test
    fun `una cadena de otra CA es rechazada aunque el sistema la confie`() {
        listOf("otra-ca-isrg-root-x1.pem", "otra-ca-digicert-g2.pem").forEach { archivo ->
            val cadena = listOf(cargar(archivo))
            assertThrows(SSLPeerUnverifiedException::class.java) { pinner.check(hostQa, cadena) }
            assertThrows(SSLPeerUnverifiedException::class.java) { pinner.check(hostProd, cadena) }
        }
    }

    @Test
    fun `solo el intermedio no basta, el pin es de las raices y no de los intermedios que Google rota`() {
        assertThrows(SSLPeerUnverifiedException::class.java) {
            pinner.check(hostQa, listOf(cargar("gts-wr2-intermedio.pem")))
        }
    }

    @Test
    fun `el patron cubre los hosts que arma urlBaseCloudFunctions para QA y produccion`() {
        // Si cambiara el formato del host (otra region, otro dominio), el pin dejaria de aplicar y la
        // conexion quedaria sin proteger sin que nada falle: este test lo detecta.
        val ajena = listOf(cargar("otra-ca-isrg-root-x1.pem"))
        listOf("kinecare-cl-qa", "kinecare-cl").forEach { proyecto ->
            val host = URI(urlBaseCloudFunctions(proyecto)).host
            assertThrows(SSLPeerUnverifiedException::class.java, { pinner.check(host, ajena) }, "sin pin para $host")
        }
    }

    @Test
    fun `otros hosts no se ven afectados por el pinning`() {
        val cadenaAjena = listOf(cargar("otra-ca-isrg-root-x1.pem"))

        pinner.check("firestore.googleapis.com", cadenaAjena)
        pinner.check("www.example.com", cadenaAjena)
    }

    @Test
    fun `el cliente HTTP de las Cloud Functions usa el CertificatePinner`() {
        val cliente = NetworkModule.provideOkHttpClient()

        // No se compara el objeto entero: OkHttp le agrega un `certificateChainCleaner` al construir el
        // cliente, asi que `equals` nunca coincidiria. Importan los pins.
        assertEquals(pinner.pins, cliente.certificatePinner.pins)
        assertEquals(4, cliente.certificatePinner.pins.size)
    }
}
