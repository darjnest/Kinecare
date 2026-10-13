package com.darjnest.kinecare.core.network.security

import okhttp3.CertificatePinner

/**
 * Patron de host de las Cloud Functions (`<region>-<proyecto>.cloudfunctions.net`): cubre QA y
 * produccion. El comodin de OkHttp reemplaza exactamente una etiqueta, que es lo que hay.
 */
internal const val HOST_CLOUD_FUNCTIONS = "*.cloudfunctions.net"

/**
 * Pins SPKI (SHA-256 de la clave publica) de las **raices** de Google Trust Services, la CA que
 * firma `*.cloudfunctions.net`.
 *
 * Por que raices y no el certificado del servidor ni un intermedio:
 * - El certificado hoja es un certificado compartido de Google que se renueva cada ~3 meses.
 * - Google emite desde varios intermedios (WR1..WR5, WE1..WE4) y los rota sin avisar.
 * - Las raices R1..R4 son estables (vencen en 2036) y cubren las cadenas RSA (R1, R2) y ECC (R3, R4):
 *   si Google cambia de linea de certificacion, otra de estas cuatro sigue coincidiendo. Son tambien
 *   el respaldo mutuo que exige un pin (un solo pin sin respaldo bloquea la app en una rotacion).
 *
 * OkHttp acepta la conexion si **cualquier** certificado de la cadena validada coincide con algun
 * pin. Hoy el servidor envia `GTS Root R1` firmada de forma cruzada por GlobalSign; esa copia tiene
 * la misma clave publica que la raiz autofirmada, asi que el pin de R1 coincide.
 *
 * Obtenidos el 2026-10-08 del almacen de confianza del sistema y contrastados con la cadena real de
 * `us-central1-kinecare-cl-qa.cloudfunctions.net` (`scripts/verificar-pins.sh` lo repite). Para
 * recalcular uno: `openssl x509 -in raiz.pem -pubkey -noout | openssl pkey -pubin -outform der |
 * openssl dgst -sha256 -binary | openssl base64`.
 *
 * Si Google cambia de CA raiz (no solo de intermedio), hay que agregar su pin **y publicar una version
 * de la app antes** de que el servidor cambie; las versiones ya instaladas con pins viejos no podran
 * llamar a las funciones hasta actualizarse. Ver docs/ARCHITECTURE.md#seguridad.
 */
internal val PINS_CLOUD_FUNCTIONS: List<String> = listOf(
    "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=", // GTS Root R1 (RSA)
    "sha256/Vfd95BwDeSQo+NUYxVEEIlvkOlWY2SalKK1lPhzOx78=", // GTS Root R2 (RSA)
    "sha256/QXnt2YHvdHR3tJYmQIr0Paosp6t/nggsEGD4QJZ3Q0g=", // GTS Root R3 (ECC)
    "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=", // GTS Root R4 (ECC)
)

/**
 * `CertificatePinner` del cliente que llama a las Cloud Functions (pagos, verificacion de
 * identidad, reservas). Un certificado emitido por otra CA (p. ej. una CA instalada por un atacante
 * o por un proxy en el telefono) falla con `SSLPeerUnverifiedException` aunque el sistema lo confie.
 * Los hosts que no coinciden con [HOST_CLOUD_FUNCTIONS] no se ven afectados.
 */
internal fun crearCertificatePinnerCloudFunctions(): CertificatePinner =
    CertificatePinner.Builder()
        .add(HOST_CLOUD_FUNCTIONS, *PINS_CLOUD_FUNCTIONS.toTypedArray())
        .build()
