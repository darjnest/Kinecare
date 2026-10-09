package com.darjnest.kinecare.core.network.security

import android.util.Log
import okhttp3.Call
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "KineCarePinning"

/**
 * Decide, en cada llamada, si el certificate pinning esta activo. Lo esta **siempre**, salvo que
 * Remote Config traiga un override firmado y vigente (ver [overrideVigente]).
 *
 * [valorOverride] y [ahoraEpochSegundos] se inyectan para probarla sin Firebase ni reloj real. Solo se
 * guarda en cache el ultimo override **valido** (por su texto exacto, hasta su `hasta`): verificar una
 * firma ECDSA en cada peticion seria un gasto inutil, pero un valor invalido se reevalua siempre, asi
 * que uno publicado para mas tarde se activa solo cuando llega su `desde`.
 */
class PinningPolicy internal constructor(
    private val valorOverride: () -> String?,
    private val ahoraEpochSegundos: () -> Long,
    private val clavePublica: String,
) {
    constructor(valorOverride: () -> String?) : this(
        valorOverride = valorOverride,
        ahoraEpochSegundos = { System.currentTimeMillis() / 1000 },
        clavePublica = CLAVE_PUBLICA_OVERRIDE_PINNING,
    )

    @Volatile private var verificado: Pair<String, Long>? = null
    @Volatile private var ultimoEstadoRegistrado: Boolean? = null

    fun pinningActivo(): Boolean {
        val ahora = ahoraEpochSegundos()
        val valor = valorOverride()
        val enCache = verificado
        val overrideActivo = if (valor != null && enCache != null && enCache.first == valor && ahora < enCache.second) {
            true
        } else {
            val vigente = overrideVigente(valor, ahora, clavePublica)
            verificado = if (vigente) valor?.let { it to hastaDe(it) } else null
            vigente
        }
        registrarCambio(pinningActivo = !overrideActivo)
        return !overrideActivo
    }

    private fun hastaDe(valor: String): Long =
        runCatching { kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<OverridePinningFirmado>(valor).hasta }
            .getOrDefault(0L)

    /** Deja un rastro en logcat solo cuando cambia el estado, no en cada llamada. */
    private fun registrarCambio(pinningActivo: Boolean) {
        if (ultimoEstadoRegistrado == pinningActivo) return
        ultimoEstadoRegistrado = pinningActivo
        if (!pinningActivo) Log.w(TAG, "Certificate pinning DESACTIVADO por un override firmado de Remote Config") else Log.i(TAG, "Certificate pinning activo")
    }
}

/**
 * `Call.Factory` que usa el cliente con pinning, o uno identico sin pinning mientras [politica]
 * indique un override vigente. Los dos comparten dispatcher y pool de conexiones, y OkHttp no reutiliza
 * entre ellos una conexion abierta con otro `CertificatePinner` (forma parte de la `Address`), asi que
 * una conexion establecida sin pinning no se reaprovecha cuando el pinning vuelve a estar activo.
 */
class PinningCallFactory internal constructor(
    private val conPinning: Call.Factory,
    private val sinPinning: Call.Factory,
    private val politica: PinningPolicy,
) : Call.Factory {

    constructor(clienteConPinning: OkHttpClient, politica: PinningPolicy) : this(
        conPinning = clienteConPinning,
        sinPinning = clienteConPinning.newBuilder().certificatePinner(CertificatePinner.DEFAULT).build(),
        politica = politica,
    )

    override fun newCall(request: Request): Call =
        (if (politica.pinningActivo()) conPinning else sinPinning).newCall(request)
}
