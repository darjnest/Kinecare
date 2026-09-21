package com.darjnest.kinecare.feature.auth.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.loginDataStore by preferencesDataStore(name = "login_preferences")

private object LoginPreferenceKeys {
    val RECORDAR_CUENTA = booleanPreferencesKey("recordar_cuenta")
    val RUT_RECORDADO = stringPreferencesKey("rut_recordado")
}

/**
 * Preferencia simple, no sensible: solo el RUT que el usuario pidió recordar.
 * Nunca guarda la contraseña ni tokens — eso vive en Keystore/EncryptedSharedPreferences
 * si en algún momento se necesita, ver docs/ARCHITECTURE.md#seguridad.
 */
@Singleton
class LoginPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun recordarCuenta(): Boolean =
        context.loginDataStore.data.map { it[LoginPreferenceKeys.RECORDAR_CUENTA] ?: true }.first()

    suspend fun rutRecordado(): String =
        context.loginDataStore.data.map { it[LoginPreferenceKeys.RUT_RECORDADO] ?: "" }.first()

    suspend fun guardar(recordar: Boolean, rut: String) {
        context.loginDataStore.edit { prefs ->
            prefs[LoginPreferenceKeys.RECORDAR_CUENTA] = recordar
            prefs[LoginPreferenceKeys.RUT_RECORDADO] = if (recordar) rut else ""
        }
    }
}
