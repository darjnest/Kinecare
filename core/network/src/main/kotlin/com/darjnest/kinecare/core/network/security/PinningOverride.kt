package com.darjnest.kinecare.core.network.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64

/**
 * Clave publica (ECDSA P-256, X.509 SPKI en base64) con la que se verifica el interruptor remoto del
 * certificate pinning. **Vacia a proposito**: mientras no haya clave, [overrideVigente] siempre da
 * `false` y nadie puede apagar el pinning. La clave privada no vive en este repo: se genera con
 * `scripts/pinning-override.sh generar-clave <archivo>` y la guarda quien administra el proyecto
 * (ver docs/ARCHITECTURE.md#seguridad); ese comando imprime la clave publica para pegar aqui.
 */
internal const val CLAVE_PUBLICA_OVERRIDE_PINNING: String = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAED9uBqCxSzQn1VmjvKPPFQHRCdmYY7tW6ojSNapirV73z0wPl+jY7bFAzXPSurmv+FDkAX48HIQS8cLBkoffTIQ=="

/** Parametro de Remote Config con el override firmado. Ausente o vacio = pinning activo. */
internal const val CLAVE_REMOTE_CONFIG_OVERRIDE_PINNING = "cf_pinning_override"

private const val VERSION_OVERRIDE = 1

/**
 * Ventana maxima de un override: 30 dias entre `desde` y `hasta`, ambos firmados. Un override
 * filtrado o firmado por error no puede dejar el pinning apagado indefinidamente, y por eso para
 * extenderlo hay que firmar otro (y pensarlo de nuevo).
 */
internal const val MAX_VENTANA_OVERRIDE_SEGUNDOS: Long = 30L * 24 * 60 * 60

/** Tolerancia al reloj del telefono atrasado respecto de `desde` (un override ya publicado no debe esperar). */
internal const val TOLERANCIA_RELOJ_SEGUNDOS: Long = 5L * 60

/**
 * Valor del parametro de Remote Config. Se firma `kinecare-pinning-override|v1|<desde>|<hasta>`
 * (segundos Unix) con ECDSA P-256 + SHA-256; `firma` es la firma DER en base64, la que produce
 * `openssl dgst -sha256 -sign`.
 */
@Serializable
internal data class OverridePinningFirmado(
    val v: Int,
    val desde: Long,
    val hasta: Long,
    val firma: String,
)

internal fun mensajeOverrideFirmado(desde: Long, hasta: Long): String =
    "kinecare-pinning-override|v$VERSION_OVERRIDE|$desde|$hasta"

private val jsonOverride = Json { ignoreUnknownKeys = true }

/**
 * `true` si [valor] es un override **valido y vigente**: JSON de la version esperada, firmado con la
 * clave privada que corresponde a [clavePublicaBase64], cuyo `desde` ya llego (con
 * [TOLERANCIA_RELOJ_SEGUNDOS]), cuyo `hasta` no ha pasado y cuya ventana no supera
 * [MAX_VENTANA_OVERRIDE_SEGUNDOS]. Cualquier otra cosa (vacio, basura, firma de otra clave, valor
 * alterado, vencido, aun no vigente, clave publica vacia o invalida) devuelve `false`: ante la duda el
 * pinning sigue activo.
 */
internal fun overrideVigente(valor: String?, ahoraEpochSegundos: Long, clavePublicaBase64: String): Boolean {
    if (valor.isNullOrBlank() || clavePublicaBase64.isBlank()) return false
    return try {
        val override = jsonOverride.decodeFromString<OverridePinningFirmado>(valor)
        if (override.v != VERSION_OVERRIDE) return false
        if (override.hasta <= override.desde) return false
        if (override.hasta - override.desde > MAX_VENTANA_OVERRIDE_SEGUNDOS) return false
        if (ahoraEpochSegundos + TOLERANCIA_RELOJ_SEGUNDOS < override.desde) return false
        if (ahoraEpochSegundos >= override.hasta) return false

        val clave = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.decode(clavePublicaBase64)))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(clave)
            update(mensajeOverrideFirmado(override.desde, override.hasta).toByteArray(Charsets.UTF_8))
            verify(Base64.decode(override.firma))
        }
    } catch (e: Exception) {
        false
    }
}
