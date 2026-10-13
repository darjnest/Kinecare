package com.darjnest.kinecare.core.network.security

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lee de Firebase Remote Config el override firmado del pinning ([CLAVE_REMOTE_CONFIG_OVERRIDE_PINNING]).
 *
 * Remote Config se descarga de `firebaseremoteconfig.googleapis.com`, un host **distinto** al de las
 * Cloud Functions y sin pinning: el interruptor sigue llegando justo cuando el pinning impide llamar
 * a las funciones. Lo que llega no se cree por venir de Remote Config sino por su firma
 * ([overrideVigente]); por eso este canal no necesita estar pineado.
 */
@Singleton
class PinningOverrideRemoto @Inject constructor() {

    private val config: FirebaseRemoteConfig get() = FirebaseRemoteConfig.getInstance()

    /** Valor ya activado (de la descarga anterior o el default vacio). Nunca lanza ni toca la red. */
    fun valor(): String? =
        runCatching { config.getString(CLAVE_REMOTE_CONFIG_OVERRIDE_PINNING).ifBlank { null } }.getOrNull()

    /**
     * Descarga y activa los valores. Sin red o con la cuota agotada se conserva lo ya activado. Un
     * intervalo minimo de 1 h acota cuanto tarda un override en llegar (y el consumo de la cuota); es
     * el tope de reaccion ante una rotacion de CA, ademas del lanzamiento de la app.
     */
    suspend fun refrescar() {
        try {
            config.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(INTERVALO_MINIMO_SEGUNDOS)
                    .setFetchTimeoutInSeconds(TIMEOUT_SEGUNDOS)
                    .build(),
            ).await()
            config.setDefaultsAsync(mapOf(CLAVE_REMOTE_CONFIG_OVERRIDE_PINNING to "")).await()
            config.fetchAndActivate().await()
        } catch (e: Exception) {
            // Sin red, throttling o Remote Config caido: se queda con lo ultimo activado.
        }
    }

    private companion object {
        const val INTERVALO_MINIMO_SEGUNDOS = 3600L
        const val TIMEOUT_SEGUNDOS = 10L
    }
}
